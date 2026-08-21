import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Full SRG -> official (dev-mapped) bytecode remapper for a Forge mod jar.
 *
 * ForgeGradle 7's userdev client runs with official-mapped vanilla names
 * (LivingEntity.tick()), but production mod jars are SRG-mapped. SpecialSource
 * only partially remaps such jars (it misses e.g. BlockState.m_61124_,
 * inherited field f_49792_, and SRG-named override declarations such as
 * AbstractGunSmithTableBlock.m_7926_), which then crashes at runtime with
 * NoSuchFieldError/NoSuchMethodError. This class performs a complete remap:
 *
 *   - renames every SRG-named method/field DECLARATION (m_<n>_ / f_<n>_ /
 *     method_<n>_ / field_<n>_) to its official name, using the reversed
 *     official->srg mapping (exact class lookup first, then a global
 *     name+descriptor fallback for inherited members);
 *   - rewrites every method/field REFERENCE (including ones targeting this
 *     jar's own renamed overrides) to the new names.
 *
 * Usage: java TaczDeobf <official->srg.tsrg.gz> <in.jar> <out.jar>
 */
public class TaczDeobf {
    private static final Pattern SRG_FIELD = Pattern.compile("f_\\d+_|field_\\d+");
    private static final Pattern SRG_METHOD = Pattern.compile("m_\\d+_|method_\\d+");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: TaczDeobf <tsrg.gz> <in.jar> <out.jar>");
            System.exit(1);
        }
        String tsrg = args[0];
        String inJar = args[1];
        String outJar = args[2];

        IMappingFile mapping;
        try (InputStream in = new GZIPInputStream(new FileInputStream(tsrg))) {
            mapping = IMappingFile.load(in).reverse(); // official->srg -> srg->official
        }

        // Exact (declared class) maps + global (name/desc) fallbacks.
        Map<String, String> fieldsExact = new HashMap<>();    // owner + "/" + srgField
        Map<String, String> methodsExact = new HashMap<>();   // owner + "/" + srgMethod + ":" + args
        Map<String, String> fieldsGlobal = new HashMap<>();   // srgField
        Map<String, String> methodsGlobal = new HashMap<>();  // srgMethod + ":" + args
        for (IMappingFile.IClass cls : mapping.getClasses()) {
            String owner = cls.getOriginal();
            for (IMappingFile.IField f : cls.getFields()) {
                String srg = f.getOriginal();
                String official = f.getMapped();
                if (srg == null || official == null || srg.equals(official)) continue;
                fieldsExact.put(owner + "/" + srg, official);
                fieldsGlobal.put(srg, official);
            }
            for (IMappingFile.IMethod m : cls.getMethods()) {
                String srg = m.getOriginal();
                String official = m.getMapped();
                String desc = m.getDescriptor();
                if (srg == null || official == null || desc == null) continue;
                String argsDesc = desc.substring(1, desc.indexOf(')'));
                methodsExact.put(owner + "/" + srg + ":" + argsDesc, official);
                methodsGlobal.put(srg + ":" + argsDesc, official);
            }
        }
        System.out.println("maps: fields=" + fieldsGlobal.size() + " methods=" + methodsGlobal.size());

        // Load all classes.
        Map<String, byte[]> entries = new HashMap<>();
        List<String> names = new ArrayList<>();
        try (ZipFile zin = new ZipFile(inJar)) {
            Enumeration<? extends ZipEntry> en = zin.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                byte[] data = readAll(zin.getInputStream(e));
                entries.put(e.getName(), data);
                if (e.getName().endsWith(".class")) names.add(e.getName());
            }
        }

        // Pass 1: decide renames for every class (declarations).
        Map<String, Map<String, String>> classMethodRenames = new HashMap<>(); // class -> (old+":"+args -> new)
        Map<String, Map<String, String>> classFieldRenames = new HashMap<>();  // class -> (old -> new)
        for (String name : names) {
            ClassNode cn = readClass(entries.get(name));
            String owner = cn.name;
            Map<String, String> mr = new HashMap<>();
            Map<String, String> fr = new HashMap<>();
            if (cn.fields != null) {
                for (FieldNode fn : cn.fields) {
                    if (SRG_FIELD.matcher(fn.name).matches()) {
                        String official = fieldsExact.get(owner + "/" + fn.name);
                        if (official == null) official = fieldsGlobal.get(fn.name);
                        if (official != null && !official.equals(fn.name)) fr.put(fn.name, official);
                    }
                }
            }
            if (cn.methods != null) {
                for (MethodNode mn : cn.methods) {
                    if (SRG_METHOD.matcher(mn.name).matches()) {
                        String argsDesc = argsPart(mn.desc);
                        String official = methodsExact.get(owner + "/" + mn.name + ":" + argsDesc);
                        if (official == null) official = methodsGlobal.get(mn.name + ":" + argsDesc);
                        if (official != null && !official.equals(mn.name)) mr.put(mn.name + ":" + argsDesc, official);
                    }
                }
            }
            if (!mr.isEmpty() || !fr.isEmpty()) {
                classMethodRenames.put(owner, mr);
                classFieldRenames.put(owner, fr);
            }
        }

        // Pass 2: apply renames.
        int renamed = 0;
        try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outJar))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                ZipEntry out = new ZipEntry(en.getKey());
                zout.putNextEntry(out);
                byte[] data = en.getValue();
                if (en.getKey().endsWith(".class")) {
                    String owner = ownerOf(en.getKey());
                    data = applyRenames(data, owner,
                            classMethodRenames.get(owner), classFieldRenames.get(owner),
                            methodsExact, methodsGlobal, fieldsExact, fieldsGlobal);
                    if (!java.util.Arrays.equals(data, en.getValue())) renamed++;
                }
                zout.write(data);
                zout.closeEntry();
            }
        }
        System.out.println("renamed classes=" + renamed + " -> " + outJar);
    }

    private static String ownerOf(String entry) {
        return entry.substring(0, entry.length() - ".class".length());
    }

    private static String argsPart(String desc) {
        int end = desc.indexOf(')');
        return desc.substring(1, end);
    }

    /**
     * Resolve the official name for a lambda's SAM method. The SAM name lives
     * in the indy NameAndType but its real descriptor (without captured
     * variables) is the first bootstrap argument (samMethodType) of
     * LambdaMetafactory. The indy call-site descriptor includes captured
     * arguments, so it cannot be used for the map lookup.
     */
    private static String remapIndySamName(InvokeDynamicInsnNode iin, Map<String, String> methodsGlobal) {
        if (!SRG_METHOD.matcher(iin.name).matches()) return null;
        // samMethodType is bsmArgs[0] for metafactory / altMetafactory.
        if (iin.bsmArgs != null && iin.bsmArgs.length >= 3 && iin.bsmArgs[0] instanceof Type samType) {
            String official = methodsGlobal.get(iin.name + ":" + argsPart(samType.getDescriptor()));
            if (official != null) return official;
        }
        // Fallback: use the call-site descriptor.
        return methodsGlobal.get(iin.name + ":" + argsPart(iin.desc));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        return in.readAllBytes();
    }

    private static ClassNode readClass(byte[] data) {
        ClassNode cn = new ClassNode();
        new ClassReader(data).accept(cn, 0);
        return cn;
    }

    private static byte[] applyRenames(byte[] data, String owner,
                                       Map<String, String> methodRenames, Map<String, String> fieldRenames,
                                       Map<String, String> methodsExact, Map<String, String> methodsGlobal,
                                       Map<String, String> fieldsExact, Map<String, String> fieldsGlobal) {
        ClassNode cn = readClass(data);
        boolean changed = false;

        // Rename declarations.
        if (cn.fields != null && fieldRenames != null) {
            for (FieldNode fn : cn.fields) {
                String official = fieldRenames.get(fn.name);
                if (official != null) {
                    fn.name = official;
                    changed = true;
                }
            }
        }
        if (cn.methods != null && methodRenames != null) {
            for (MethodNode mn : cn.methods) {
                String official = methodRenames.get(mn.name + ":" + argsPart(mn.desc));
                if (official != null) {
                    mn.name = official;
                    changed = true;
                }
            }
        }

        // Rewrite references.
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode min) {
                        String newName = null;
                        String argsDesc = argsPart(min.desc);
                        if (min.owner.equals(owner) && methodRenames != null) {
                            newName = methodRenames.get(min.name + ":" + argsDesc);
                        }
                        if (newName == null && SRG_METHOD.matcher(min.name).matches()) {
                            newName = methodsExact.get(min.owner + "/" + min.name + ":" + argsDesc);
                            if (newName == null) newName = methodsGlobal.get(min.name + ":" + argsDesc);
                        }
                        if (newName != null && !newName.equals(min.name)) {
                            min.name = newName;
                            changed = true;
                        }
                    } else if (insn instanceof FieldInsnNode fin) {
                        String newName = null;
                        if (fin.owner.equals(owner) && fieldRenames != null) {
                            newName = fieldRenames.get(fin.name);
                        }
                        if (newName == null && SRG_FIELD.matcher(fin.name).matches()) {
                            newName = fieldsExact.get(fin.owner + "/" + fin.name);
                            if (newName == null) newName = fieldsGlobal.get(fin.name);
                        }
                        if (newName != null && !newName.equals(fin.name)) {
                            fin.name = newName;
                            changed = true;
                        }
                    } else if (insn instanceof InvokeDynamicInsnNode iin) {
                        // Lambdas / method references carry the SAM method name in the
                        // indy NameAndType and the implementation handle in bsmArgs.
                        // The SAM name is in SRG form (e.g. m_247679_) and must be
                        // remapped or the generated lambda class fails to implement
                        // the (renamed) interface method -> AbstractMethodError.
                        String newSam = remapIndySamName(iin, methodsGlobal);
                        if (newSam != null && !newSam.equals(iin.name)) {
                            iin.name = newSam;
                            changed = true;
                        }
                        // Remap method/field references carried as bootstrap handles
                        // (e.g. Foo::m_12345_ or a field getter reference).
                        if (iin.bsmArgs != null) {
                            for (int i = 0; i < iin.bsmArgs.length; i++) {
                                if (!(iin.bsmArgs[i] instanceof Handle h)) continue;
                                int tag = h.getTag();
                                if (tag == Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_INVOKESTATIC
                                        || tag == Opcodes.H_INVOKESPECIAL || tag == Opcodes.H_INVOKEINTERFACE
                                        || tag == Opcodes.H_NEWINVOKESPECIAL) {
                                    String argsDesc = argsPart(h.getDesc());
                                    String nn = methodsExact.get(h.getOwner() + "/" + h.getName() + ":" + argsDesc);
                                    if (nn == null && SRG_METHOD.matcher(h.getName()).matches()) {
                                        nn = methodsGlobal.get(h.getName() + ":" + argsDesc);
                                    }
                                    if (nn != null && !nn.equals(h.getName())) {
                                        iin.bsmArgs[i] = new Handle(tag, h.getOwner(), nn, h.getDesc(), h.isInterface());
                                        changed = true;
                                    }
                                } else if (tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC
                                        || tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC) {
                                    String nn = fieldsExact.get(h.getOwner() + "/" + h.getName());
                                    if (nn == null && SRG_FIELD.matcher(h.getName()).matches()) {
                                        nn = fieldsGlobal.get(h.getName());
                                    }
                                    if (nn != null && !nn.equals(h.getName())) {
                                        iin.bsmArgs[i] = new Handle(tag, h.getOwner(), nn, h.getDesc(), h.isInterface());
                                        changed = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!changed) return data;
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
