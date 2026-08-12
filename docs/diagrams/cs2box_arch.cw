# session_id: 42203839-9ba7-40c8-95cd-bcd305fbf027
classes: {
  zone_1: {
    style: {
      fill: "#F0F5FA"
      stroke: "#4185BF"
      font-color: "#333333"
      border-radius: 8
    }
  }
  zone_2: {
    style: {
      fill: "#F2F7FB"
      stroke: "#4185BF"
      font-color: "#333333"
      border-radius: 8
    }
  }
  zone_3: {
    style: {
      fill: "#F6F9FC"
      stroke: "#4185BF"
      font-color: "#333333"
      border-radius: 8
    }
  }
  zone_4: {
    style: {
      fill: "#F9FBFD"
      stroke: "#4185BF"
      font-color: "#333333"
      border-radius: 8
    }
  }
  zone_5: {
    style: {
      fill: "#FCFDFE"
      stroke: "#4185BF"
      font-color: "#333333"
      border-radius: 8
    }
  }
  entity: {
    style: {
      fill: "#FFFFFF"
      stroke: "#1F2937"
      font-color: "#333333"
      border-radius: 6
      shadow: true
    }
  }
  signal: {
    style: {
      fill: transparent
      font-color: "#6B7280"
    }
  }
}

direction: right

title: CS2-Box Architecture {
  shape: text
  class: entity
  style.font-size: 28
  style.bold: true
}

platforms: {
  class: zone_1
  label: Platform Modules

  v1_21_1: {
    class: entity
    label: |`md
      **v1_21_1** (legacy)
      NeoForge 21.x · Java 21
      TACZ optional · ModList.isLoaded downgrade
    `|
  }

  v26_1_2: {
    class: entity
    label: |`md
      **v26_1_2** (decoupled)
      NeoForge 26.x · Java 25+preview
    `|
  }

  v26_2: {
    class: entity
    label: |`md
      **v26_2** (decoupled)
      26.x 小改 · HudVisibility 适配
    `|
    tooltip: "decoupled era: AnimRenderOps API adaptation diff"
  }

  forge_26_1_2: {
    class: signal
    style.stroke-dash: 3
    label: |`md
      _forge_26_1_2_
      experimental · uncommitted
    `|
  }

  archived: {
    shape: text
    class: signal
    style.opacity: 0.4
    label: Archived: v1_21_0/3/4/5/8/10/11
  }
}

common: {
  class: zone_2
  label: |`md
    **common module**
    shared code + resources
    (textures / sounds / lang / recipes / achievements)
  `|
}

# 1+2. dependency & resource flow (compile-time)
platforms -> common: compile dependency + shared resources (srcDir)

# 3. rendering primitive flow (runtime)
gui_helpers: GUI screens & logic helpers {
  class: entity
}

anim_ops: AnimRenderOps facade {
  class: entity
  shape: hexagon
  label: |`md
    **utils/AnimRenderOps.java**
    `// era: legacy|decoupled`
    per-platform facade
  `|
}

primitives: {
  class: entity
  label: |`md
    13 public rendering primitives (per-platform impl)
    blitTextured ×3 · fill · fillGradient · scissor
    scissorDisable · setBlendNormal · flush
    renderBlurredBackground · renderItem2D · renderItem3D · supports3D
  `|
}

gui_helpers -> anim_ops: call facade
anim_ops -> primitives: implement

check_animops: scripts/check-animops-drift.sh {
  class: signal
  shape: page
}
check_animops -> anim_ops: verify signature consistency

# 4. architecture constraint (compile-time gate)
check_arch: :common:checkCommonArchitecture {
  class: signal
  shape: page
}
constraint_001: CONSTRAINT-001 (no net.minecraft/* / net.neoforged/*) {
  class: signal
  shape: diamond
}
common -> check_arch: enforce
check_arch -> constraint_001: block on violation
constraint_001 -> common: gate on compileJava

# 5. version sync (release)
version_files: {
  class: entity
  label: |`md
    gradle.properties
    neoforge.mods.toml
    CHANGELOG.md
    README.md
  `|
}
mod_version: mod_version {
  class: entity
}
check_version: scripts/check-version.sh {
  class: signal
  shape: page
}
mod_version -> version_files: must sync
version_files -> check_version: verify
check_version -> version_files: pass/fail

# 6. mirror discipline (development)
mirror: scripts/mirror.sh {
  class: signal
  shape: page
}
mirror_decision: API adaptation diff? {
  class: signal
  shape: diamond
}
platforms.v26_1_2 -> mirror: new changes
mirror -> mirror_decision: sync
mirror_decision -> platforms.v26_2: |`md
  **targeted merge** (API diff)
  **copy** (pure new file)
`|
platforms.(v26_1_2 -> v26_2): forbidden: whole-file overwrite {
  class: signal
  style.stroke-dash: 3
}

# build constraints edge note
build_note: {
  shape: text
  class: signal
  label: |`md
    **Build constraints**
    NeoGradle 7.1.38 · wrapper 9.5.1
    one MC version per Gradle invocation (`-Pactive_versions=<v>`, default 26.1.2)
  `|
}