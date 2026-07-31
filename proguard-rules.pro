# ProGuard / R8 minify rules for CS2-Box mod.
# Applied via: ./gradlew :v1_21_1:minifyJar -Pactive_versions=1.21.1
# (or :v26_1_2 / :v26_2). Output: build/libs/csgobox-<mc>-1.0.7-minified.jar.

# ---- 1. Mod entry point ----
# The @Mod entry class is discovered by string-key lookup in mods.toml.
-keep @net.neoforged.fml.common.Mod class * { <init>(...); }
-keepclassmembers class * {
    @net.neoforged.bus.api.SubscribeEvent <methods>;
    @net.neoforged.fml.event.lifecycle.* <methods>;
    @net.neoforged.neoforge.event.* <methods>;
}

# ---- 2. EventBusSubscriber auto-registration ----
# Keep no-arg constructor + all event-handler methods.
-keepclassmembers @net.neoforged.bus.api.EventBusSubscriber class * {
    <init>();
    public *;
}

# ---- 3. Networking: CustomPacketPayload records ----
# Packet CsgoProgress / BulkProgress / BoxOpenResult / SyncBoxItems are
# deserialised by StreamCodec via the TYPE field found reflectively
# by payload id. Keep record components, TYPE/STREAM_CODEC fields,
# and the canonical constructor.
-keep class com.reclizer.csgobox.**.packet.** implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static ** TYPE;
    public static ** STREAM_CODEC;
    public <init>(...);
    public ** type();
    public ** write(...);
    public ** id();
    public ** streamCodec();
}

# ---- 4. Capability attachment data (NBT-serialised) ----
# CsboxPlayerData read from NBT / Capability by Forge via reflection
# on the no-arg constructor + Codec/StreamCodec fields.
-keep class com.reclizer.csgobox.**.capability.** { *; }
-keepclassmembers class * implements net.neoforged.neoforge.attachment.IAttachmentHolder { *; }
-keepclassmembers class com.reclizer.csgobox.**.capability.ModCapability$* { *; }

# ---- 5. Static init / JSON load / item registration ----
# ItemCsgoBox / BoxRegistry / BoxJsonLoader run side effects in <clinit>
# and during CommonSetup; getDefaultInstance is reflectively called by
# Minecraft's ItemStack network codec.
-keepclassmembers class com.reclizer.csgobox.**.item.ItemCsgoBox { *; }
-keep class com.reclizer.csgobox.**.box.BoxRegistry { *; }
-keep class com.reclizer.csgobox.**.box.BoxJsonLoader { *; }
-keep class com.reclizer.csgobox.**.box.BoxDefaults { *; }
-keep class com.reclizer.csgobox.**.box.BoxFileWatcher { *; }
-keepclassmembers class * extends net.minecraft.world.item.Item { <init>(...); }

# ---- 6. Config holders ----
# CsboxConfig and @EventBusSubscriber-decorated enums accessed by NeoForge
# via reflection on field name.
-keep @net.neoforged.neoforge.common.ModConfigSpec$* class * { *; }
-keep class com.reclizer.csgobox.**.config.** { *; }
-keepclassmembers enum com.reclizer.csgobox.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- 7. Open-platform common/ utility (no MC dep) ----
# BoxFileWatcher / BoxDefaults / TutorialFetcher / TutorialSources /
# BoxJsonSchemaValidator live in common/ and are also referenced from
# the 3 platform-specific entry points.
-keep class com.reclizer.csgobox.box.BoxFileWatcher { *; }
-keep class com.reclizer.csgobox.box.BoxDefaults { *; }
-keep class com.reclizer.csgobox.box.TutorialFetcher { *; }
-keep class com.reclizer.csgobox.box.TutorialSources { *; }
-keep class com.reclizer.csgobox.box.TutorialSources$Source { *; }
-keep class com.reclizer.csgobox.box.BoxJsonSchemaValidator { *; }
-keep class com.reclizer.csgobox.box.BoxJsonSchemaValidator$SchemaIssue { *; }
-keep class com.reclizer.csgobox.utils.ColorTools { *; }
-keep class com.reclizer.csgobox.utils.OverlayColor { *; }

# ---- Standard NeoForge / Minecraft library suppressions ----
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn net.sf.jopt-simple.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Preserve annotations and enum reflection surfaces.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation,allowshrinking enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep @Keep and @OnlyIn annotations.
-keep @interface net.neoforged.neoforge.common.annotations.* { *; }
-keep @interface net.neoforged.api.distmarker.OnlyIn { *; }

# Keep record component accessors (Java 16+ records rely on these for
# reflection in vanilla code, e.g. CustomPacketPayload implementations).
-keepclassmembers class * extends java.lang.Record {
    public <fields>;
}
