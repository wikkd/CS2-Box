z g# ProGuard / R8 minify rules for CS2-Box mod.
# Goal: shrink the production jar to ~50% of the unminified size by removing
# unused classes/methods, while keeping every reflection / annotation / network
# surface that NeoForge 21.1.115 / 26.x uses to load and dispatch the mod.
#
# Keep rule categories below:
#   1. Mod entry point and registration
#   2. NeoForge event subscribers (@SubscribeEvent, @EventBusSubscriber)
#   3. Networking: packet payloads + custom payload registration
#   4. Capability attachment data (CsboxPlayerData) — NBT-serialised
#   5. Static initialisers that load JSON / register items / schedule tasks
#   6. Native Minecraft fields NeoForge references via AccessTransformer
#   7. Open-platform common/ utility classes that other mods may call
#
# Apply with: ./gradlew :v1_21_1:minifyJar -Pactive_versions=1.21.1
# (or :v26_1_2 / :v26_2). Output: build/libs/csgobox-<mc>-1.0.7-minified.jar.

# ---- 1. Mod entry point ----
# The @Mod entry class is discovered by string-key lookup in mods.toml; its
# public methods (constructor + @SubscribeEvent on RegisterPayloadHandlers /
# CommonSetup / ClientSetup events) must remain.
-keep @net.neoforged.fml.common.Mod class * { <init>(...); }
-keepclassmembers class * {
    @net.neoforged.bus.api.SubscribeEvent <methods>;
    @net.neoforged.fml.event.lifecycle.* <methods>;
    @net.neoforged.neoforge.event.* <methods>;
}

# ---- 2. EventBusSubscriber auto-registration ----
# mod EventBusSubscriber classes are registered reflectively by class name;
# keep the no-arg constructor + all event-handler methods.
-keepclassmembers @net.neoforged.bus.api.EventBusSubscriber class * {
    <init>();
    public *;
}
-keep class com.reclizer.csgobox.**$ClientModEvents { *; }

# ---- 3. Networking: CustomPacketPayload records ----
# PacketCsgoProgress / PacketBoxOpenResult / PacketCsgoBulkResult / etc. are
# deserialised by StreamCodec via the TYPE field, which is found reflectively
# by payload id. Keep the record components, the TYPE field, the STREAM_CODEC
# field, and the canonical constructor.
-keep class com.reclizer.csgobox.**.packet.** implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static ** TYPE;
    public static ** STREAM_CODEC;
    public <init>(...);
    public ** type();
    public ** write(...);
    public ** id();
    public ** streamCodec();
}
# Payload types are looked up by ResourceLocation id in the registry; keep the
# public record components.
-keepclassmembers class com.reclizer.csgobox.**.packet.Packet* {
    <init>(...);
    public <fields>;
    public *** *(...);
}

# ---- 4. Capability attachment data ----
# CsboxPlayerData is read from NBT / Capability by Forge via reflection on
# the no-arg constructor + the Codec/StreamCodec fields. Same for any
# other Capability.* reference class.
-keep class com.reclizer.csgobox.**.capability.** { *; }
-keepclassmembers class * implements net.neoforged.neoforge.attachment.IAttachmentHolder { *; }
-keepclassmembers class com.reclizer.csgobox.**.capability.ModCapability$* { *; }

# ---- 5. Static init / JSON load / item registration ----
# ItemCsgoBox / BoxRegistry / BoxJsonLoader all run side effects in
# <clinit> (static blocks) and during CommonSetup; their getDefaultInstance
# is also reflectively called by Minecraft's ItemStack network codec.
-keepclassmembers class com.reclizer.csgobox.**.item.ItemCsgoBox { *; }
-keep class com.reclizer.csgobox.**.box.BoxRegistry { *; }
-keep class com.reclizer.csgobox.**.box.BoxJsonLoader { *; }
-keep class com.reclizer.csgobox.**.box.BoxDefaults { *; }
-keep class com.reclizer.csgobox.**.box.BoxFileWatcher { *; }
-keepclassmembers class * extends net.minecraft.world.item.Item { <init>(...); }
-keepclassmembers class * extends net.minecraft.world.level.block.Block { <init>(...); }
-keepclassmembers class * extends net.minecraft.world.entity.Entity { <init>(...); }

# ---- 6. AccessTransformer / Mixin / Config holders ----
# CsboxConfig and the @EventBusSubscriber-decorated enums are accessed by
# NeoForge via reflection on the field name.
-keep @net.neoforged.neoforge.common.ModConfigSpec$* class * { *; }
-keep class com.reclizer.csgobox.**.config.** { *; }
-keepclassmembers enum * { *; }

# ---- 7. Open-platform common/ utility (no MC dep) ----
# BoxFileWatcher / BoxDefaults / TutorialFetcher / TutorialSources /
# BoxJsonSchemaValidator live in common/ and are also referenced from the
# 3 platform-specific entry points. Keep public surface to avoid breaking
# cross-platform linkage.
-keep class com.reclizer.csgobox.box.BoxFileWatcher { *; }
-keep class com.reclizer.csgobox.box.BoxDefaults { *; }
-keep class com.reclizer.csgobox.box.TutorialFetcher { *; }
-keep class com.reclizer.csgobox.box.TutorialSources { *; }
-keep class com.reclizer.csgobox.box.TutorialSources$Source { *; }
-keep class com.reclizer.csgobox.box.BoxJsonSchemaValidator { *; }
-keep class com.reclizer.csgobox.box.BoxJsonSchemaValidator$SchemaIssue { *; }
-keep class com.reclizer.csgobox.utils.ColorTools { *; }
-keep class com.reclizer.csgobox.utils.OverlayColor { *; }

# ---- Common NeoForge / Minecraft keep rules (standard) ----
# These are the well-known keep rules every ProGuard config for a NeoForge
# mod needs. They cover Minecraft's reachability / Forge's reflection.

# Don't warn about library classes missing in the ProGuard classpath.
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

# Keep public API surface of every csgo-box class (entry points / @SubscribeEvent
# handlers are already covered above; this is the belt-and-braces for anything
# we forgot).
-keep class com.reclizer.csgobox.** { public *; }

# Preserve annotations and enum values/reflection surfaces.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation,allowshrinking enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Don't shrink anything we explicitly @Keep-ed.
-keep @interface net.neoforged.neoforge.common.annotations.* { *; }
-keep @interface net.neoforged.api.distmarker.OnlyIn { *; }

# Keep record component accessors (Java 16+ records rely on these for
# reflection in vanilla code, e.g. CustomPacketPayload implementations).
-keepclassmembers class * extends java.lang.Record {
    public <fields>;
}
