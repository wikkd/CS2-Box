package com.reclizer.csgobox.forge_1_20_1.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only browser over the loaded TACZ registries for {@code /csbox tacz list}.
 *
 * <p>The pure helpers (sort / filter / paginate / JSON) never touch TACZ
 * classes and are unit-testable. The collectors follow the repo's
 * optional-dependency discipline: {@code ModList.isLoaded("tacz")} gate first,
 * every TACZ touch wrapped in {@code try/catch}, degrade to empty data on any
 * failure so a missing/incompatible TACZ can never crash the command.</p>
 */
public final class TaczList {

    private static final Gson GSON = new Gson();
    private static final String TACZ_MOD_ID = "tacz";

    /** Canonical TACZ attachment slot names (match {@code AttachmentType}). */
    public static final List<String> SLOT_NAMES = List.of(
            "SCOPE", "MUZZLE", "STOCK", "GRIP", "LASER", "EXTENDED_MAG");

    public enum Kind { GUNS, AMMO, ATTACHMENTS }

    /** One registry entry. {@code fields} carries kind-specific metadata:
     *  guns: type / ammo / magazine; ammo: stack / guns; attachments: slot. */
    public record Row(String id, String name, Map<String, String> fields) {
        public Row {
            fields = Map.copyOf(fields);
        }

        public static Row of(String id, String name, String... kv) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                fields.put(kv[i], kv[i + 1]);
            }
            return new Row(id, name, fields);
        }
    }

    /** Snapshot of all three registries plus a summary count. */
    public record TaczData(int gunCount, int ammoCount, int attachmentCount, int packCount,
                           List<Row> guns, List<Row> ammo, List<Row> attachments) {
        public TaczData {
            guns = List.copyOf(guns);
            ammo = List.copyOf(ammo);
            attachments = List.copyOf(attachments);
        }

        public static TaczData empty() {
            return new TaczData(0, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }

    private TaczList() {
    }

    // ------------------------------------------------------------------
    // Pure helpers (no TACZ classes — unit-testable)
    // ------------------------------------------------------------------

    /** Sorts rows by full id (namespace:path). Returns a new list. */
    public static List<Row> sortById(List<Row> rows) {
        List<Row> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(Row::id));
        return List.copyOf(copy);
    }

    /** Keeps rows whose id namespace starts with {@code ns}. Blank = all. */
    public static List<Row> filterNs(List<Row> rows, String ns) {
        if (ns == null || ns.isBlank()) {
            return List.copyOf(rows);
        }
        String prefix = ns.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(r -> r.id().toLowerCase(Locale.ROOT).startsWith(prefix + ":"))
                .toList();
    }

    /** Keeps rows whose field {@code key} exactly equals {@code value}. */
    public static List<Row> filterField(List<Row> rows, String key, String value) {
        if (value == null || value.isBlank()) {
            return List.copyOf(rows);
        }
        String want = value.trim();
        return rows.stream().filter(r -> want.equals(r.fields().get(key))).toList();
    }

    /** Keeps rows whose comma-separated field {@code key} contains {@code token}. */
    public static List<Row> filterContains(List<Row> rows, String key, String token) {
        if (token == null || token.isBlank()) {
            return List.copyOf(rows);
        }
        String want = token.trim();
        return rows.stream().filter(r -> {
            String value = r.fields().get(key);
            if (value == null || value.isBlank()) {
                return false;
            }
            for (String part : value.split(",")) {
                if (part.trim().equals(want)) {
                    return true;
                }
            }
            return false;
        }).toList();
    }

    /** Number of pages for a list of {@code size} with {@code pageSize} rows each. */
    public static int pageCount(int size, int pageSize) {
        if (size <= 0 || pageSize <= 0) {
            return 1;
        }
        return (size + pageSize - 1) / pageSize;
    }

    /** Clamps a 1-based page into [1, lastPage]; empty lists return 1. */
    public static int clampPage(int size, int page, int pageSize) {
        if (size <= 0) {
            return 1;
        }
        if (page < 1) {
            return 1;
        }
        return Math.min(page, pageCount(size, pageSize));
    }

    /** The rows belonging to the clamped page. */
    public static List<Row> pageRows(List<Row> rows, int page, int pageSize) {
        if (rows.isEmpty() || pageSize <= 0) {
            return List.of();
        }
        int p = clampPage(rows.size(), page, pageSize);
        int from = (p - 1) * pageSize;
        int to = Math.min(from + pageSize, rows.size());
        return List.copyOf(rows.subList(from, to));
    }

    /** Serializes rows to a JSON array: {@code {id, name?, ...fields}}. */
    public static String toJson(List<Row> rows) {
        JsonArray arr = new JsonArray();
        for (Row row : rows) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", row.id());
            if (row.name() != null && !row.name().isBlank()) {
                obj.addProperty("name", row.name());
            }
            for (Map.Entry<String, String> e : row.fields().entrySet()) {
                obj.addProperty(e.getKey(), e.getValue());
            }
            arr.add(obj);
        }
        return GSON.toJson(arr);
    }

    // ------------------------------------------------------------------
    // TACZ-touching collectors (gated + guarded)
    // ------------------------------------------------------------------

    /** Whether TACZ is installed (safe even before FML is fully up). */
    public static boolean isTaczLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(TACZ_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Snapshot of every loaded gun / ammo / attachment index (server-side). */
    public static TaczData collect() {
        if (!isTaczLoaded()) {
            return TaczData.empty();
        }
        try {
            List<Row> guns = new ArrayList<>();
            List<Row> ammoRows = new ArrayList<>();
            List<Row> attachRows = new ArrayList<>();
            Set<String> packs = new HashSet<>();

            // Pass 1: guns — also builds the ammo → guns reverse map.
            Map<String, List<String>> ammoToGuns = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, CommonGunIndex> e : TimelessAPI.getAllCommonGunIndex()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String id = e.getKey().toString();
                packs.add(e.getKey().getNamespace());
                CommonGunIndex index = e.getValue();
                GunData gunData = null;
                try {
                    gunData = index.getGunData();
                } catch (Throwable ignored) {
                    // Some packs may lack gun data; still list the id.
                }
                String ammoId = gunData != null && gunData.getAmmoId() != null
                        ? gunData.getAmmoId().toString() : "";
                int magazine = gunData != null ? gunData.getAmmoAmount() : 0;
                String type = index.getType() != null ? index.getType() : "";
                String name = gunPojoName(index.getPojo());
                guns.add(Row.of(id, name, "type", type, "ammo", ammoId,
                        "magazine", String.valueOf(magazine)));
                if (!ammoId.isEmpty()) {
                    ammoToGuns.computeIfAbsent(ammoId, k -> new ArrayList<>()).add(id);
                }
            }

            // Pass 2: ammo.
            for (Map.Entry<ResourceLocation, CommonAmmoIndex> e : TimelessAPI.getAllCommonAmmoIndex()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String id = e.getKey().toString();
                packs.add(e.getKey().getNamespace());
                CommonAmmoIndex index = e.getValue();
                String name = ammoPojoName(index.getPojo());
                int stack = index.getStackSize();
                List<String> gunsUsing = ammoToGuns.getOrDefault(id, List.of());
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("stack", String.valueOf(stack));
                fields.put("guns", String.join(",", limit(gunsUsing, 6)));
                ammoRows.add(new Row(id, name, fields));
            }

            // Pass 3: attachments.
            for (Map.Entry<ResourceLocation, CommonAttachmentIndex> e : TimelessAPI.getAllCommonAttachmentIndex()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String id = e.getKey().toString();
                packs.add(e.getKey().getNamespace());
                CommonAttachmentIndex index = e.getValue();
                String name = attachmentPojoName(index.getPojo());
                String slot = slotName(index.getType());
                attachRows.add(Row.of(id, name, "slot", slot));
            }

            List<Row> sortedGuns = sortById(guns);
            List<Row> sortedAmmo = sortById(ammoRows);
            List<Row> sortedAttachments = sortById(attachRows);
            return new TaczData(sortedGuns.size(), sortedAmmo.size(), sortedAttachments.size(),
                    packs.size(), sortedGuns, sortedAmmo, sortedAttachments);
        } catch (Throwable t) {
            return TaczData.empty();
        }
    }

    /** Sorted loaded gun ids (for command suggestions). */
    public static List<String> collectGunIds() {
        List<String> out = new ArrayList<>();
        if (!isTaczLoaded()) {
            return out;
        }
        try {
            for (Map.Entry<ResourceLocation, CommonGunIndex> e : TimelessAPI.getAllCommonGunIndex()) {
                if (e.getKey() != null) {
                    out.add(e.getKey().toString());
                }
            }
        } catch (Throwable ignored) {
            // Suggestions are best-effort.
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Sorted loaded ammo ids (for command suggestions). */
    public static List<String> collectAmmoIds() {
        List<String> out = new ArrayList<>();
        if (!isTaczLoaded()) {
            return out;
        }
        try {
            for (Map.Entry<ResourceLocation, CommonAmmoIndex> e : TimelessAPI.getAllCommonAmmoIndex()) {
                if (e.getKey() != null) {
                    out.add(e.getKey().toString());
                }
            }
        } catch (Throwable ignored) {
            // Suggestions are best-effort.
        }
        out.sort(String::compareTo);
        return out;
    }

    private static String slotName(AttachmentType type) {
        // Enum constant names match TACZ's attachment NBT slot keys
        // (AttachmentSCOPE, AttachmentMUZZLE, …).
        return type != null ? type.name() : "";
    }

    private static String gunPojoName(GunIndexPOJO pojo) {
        return pojo != null && pojo.getName() != null ? pojo.getName() : "";
    }

    private static String ammoPojoName(AmmoIndexPOJO pojo) {
        return pojo != null && pojo.getName() != null ? pojo.getName() : "";
    }

    private static String attachmentPojoName(AttachmentIndexPOJO pojo) {
        return pojo != null && pojo.getName() != null ? pojo.getName() : "";
    }

    private static List<String> limit(List<String> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        List<String> out = new ArrayList<>(list.subList(0, max));
        out.add("…+" + (list.size() - max));
        return out;
    }
}