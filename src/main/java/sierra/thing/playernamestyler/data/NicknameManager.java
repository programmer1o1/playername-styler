package sierra.thing.playernamestyler.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import sierra.thing.playernamestyler.PlayerNameStyler;

public class NicknameManager {
    private Map<UUID, String> nicknames = new HashMap<UUID, String>();
    private final File nicknamesFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public NicknameManager() {
        this.nicknamesFile = new File("config/playernamestyler/nicknames.json");
        this.nicknamesFile.getParentFile().mkdirs();
    }

    public void setNickname(UUID playerUUID, String nickname) {
        this.nicknames.put(playerUUID, nickname);
        this.saveNicknames();
    }

    public String getNickname(UUID playerUUID) {
        return this.nicknames.get(playerUUID);
    }

    public void removeNickname(UUID playerUUID) {
        if (this.nicknames.remove(playerUUID) != null) {
            this.saveNicknames();
        }
    }

    public void loadNicknames() {
        if (!this.nicknamesFile.exists()) {
            PlayerNameStyler.LOGGER.info("Nicknames file not found. Creating a new one.");
            this.saveNicknames();
            return;
        }
        try (FileReader reader = new FileReader(this.nicknamesFile);){
            Type type = new TypeToken<HashMap<UUID, String>>(){}.getType();
            Map<UUID, String> loaded = this.gson.fromJson((Reader)reader, type);
            this.nicknames = loaded != null ? loaded : new HashMap<>();
            PlayerNameStyler.LOGGER.info("Loaded {} nicknames from file", this.nicknames.size());
        }
        catch (IOException e) {
            PlayerNameStyler.LOGGER.error("Failed to load nicknames: ", (Throwable)e);
            this.nicknames = new HashMap<UUID, String>();
        }
    }

    public void saveNicknames() {
        try (FileWriter writer = new FileWriter(this.nicknamesFile);){
            this.gson.toJson(this.nicknames, (Appendable)writer);
            PlayerNameStyler.LOGGER.info("Saved {} nicknames to file", (Object)this.nicknames.size());
        }
        catch (IOException e) {
            PlayerNameStyler.LOGGER.error("Failed to save nicknames: ", (Throwable)e);
        }
    }
}
