package com.reclizer.csgobox.config;

import com.google.gson.*;
import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.item.ItemCsgoBox;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.reclizer.csgobox.CsgoBox.LOGGER;

public class CsgoBoxManage {
    public static List<ItemCsgoBox.BoxInfo> BOX = new ArrayList<>();

    private static final JsonParser JSON_PARSER = new JsonParser();

    public static void loadConfigBox() throws IOException {
        Path configPath = FMLPaths.CONFIGDIR.get();
        Path folderPath = configPath.resolve("csbox");

        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }

        List<ItemCsgoBox.BoxInfo> BOX = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
            for (Path path : stream) {
                if (!path.getFileName().toString().endsWith(".json")) continue;

                try (InputStreamReader input = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    JsonObject json = JSON_PARSER.parse(input).getAsJsonObject();
                    Gson gson = new Gson();
                    ItemCsgoBox.BoxInfo info = gson.fromJson(json, ItemCsgoBox.BoxInfo.class);
                    BOX.add(info);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        CsgoBoxManage.BOX = BOX;
    }

    public static void updateBoxJson(String name, List<String> item, List<Integer> grade) throws IOException {
        Path configPath = FMLPaths.CONFIGDIR.get();
        Path folderPath = configPath.resolve("csbox");

        List<ItemCsgoBox.BoxInfo> oldBox = new ArrayList<>(BOX);

        List<String> grade1 = new ArrayList<>();
        List<String> grade2 = new ArrayList<>();
        List<String> grade3 = new ArrayList<>();
        List<String> grade4 = new ArrayList<>();
        List<String> grade5 = new ArrayList<>();

        for (int idx = 0; idx < grade.size() && idx < item.size(); idx++) {
            Integer g = grade.get(idx);
            if (g == null) continue;
            String it = item.get(idx);
            switch (g) {
                case 1 -> grade1.add(it);
                case 2 -> grade2.add(it);
                case 3 -> grade3.add(it);
                case 4 -> grade4.add(it);
                case 5 -> grade5.add(it);
            }
        }

        ItemCsgoBox.BoxInfo newBox = new ItemCsgoBox.BoxInfo();
        newBox.boxName = name;
        newBox.boxRandom = new int[]{2, 5, 25, 125, 625};
        newBox.grade1 = grade1;
        newBox.grade2 = grade2;
        newBox.grade3 = grade3;
        newBox.grade4 = grade4;
        newBox.grade5 = grade5;

        CsgoBoxManage.BOX = new ArrayList<>();
        boolean replaced = false;
        for (ItemCsgoBox.BoxInfo info : oldBox) {
            if (info.boxName.equals(name)) {
                CsgoBoxManage.BOX.add(newBox);
                replaced = true;
            } else {
                CsgoBoxManage.BOX.add(info);
            }
        }
        if (!replaced) {
            CsgoBoxManage.BOX.add(newBox);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(newBox);

        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\.\\.", "_");
        Path filePath = folderPath.resolve(safeName + ".json");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            writer.write(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}