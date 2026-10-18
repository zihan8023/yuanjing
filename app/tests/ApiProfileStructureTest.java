import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ApiProfileStructureTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String read(Path root,String file)throws Exception{return new String(Files.readAllBytes(root.resolve(file)),StandardCharsets.UTF_8);}
    public static void main(String[]args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String profile=read(root,"src/com/termux/app/iqcode/model/ApiProfile.java");
        String config=read(root,"src/com/termux/app/iqcode/model/SessionConfig.java");
        String settings=read(root,"src/com/termux/app/iqcode/storage/ApiSettingsStore.java");
        String secrets=read(root,"src/com/termux/app/iqcode/security/AndroidSecretStore.java");
        String ui=read(root,"src/com/iqge/MainActivity.java");
        require(profile.contains("class ApiProfile")&&profile.contains("credentialRevision")&&!profile.contains("apiKey"),"profile metadata must never contain an API key");
        require(config.contains("profileId")&&config.contains("profileRevision")&&config.contains("credentialRevision")&&config.contains("c.profileId = profileId"),"turn configs must carry profile revisions in copies");
        require(secrets.contains("setApiKey(String profileId, int credentialRevision")&&secrets.contains("getApiKey(String profileId, int credentialRevision")&&secrets.contains("removeApiKey(String profileId, int credentialRevision"),"secrets must use per-profile revision slots");
        require(settings.contains("migrateLegacyProfile")&&settings.contains("api_profiles_v1")&&settings.contains("saveProfile")&&settings.contains("deleteProfile")&&settings.contains("resolveProfile"),"settings store must migrate and manage multiple profiles");
        require(!settings.contains("OFFICIAL_")&&!settings.contains("iqcode-official")&&!settings.contains("MAX_API_PROFILES")&&settings.contains("profiles.isEmpty()")&&!settings.contains("At least one API profile is required"),"no vendor API record may be hard-coded and every record must stay deletable, down to zero");
        require(ui.contains("showApiProfileManager")&&ui.contains("showApiProfileEditor")&&ui.contains("API 配置记录")&&!ui.contains("input(config.apiKey,true)"),"settings UI must manage records without refilling secrets");
        require(!ui.contains("MAX_API_PROFILES")&&!ui.contains("官方 API")&&ui.contains("TextView add=pill(\"＋ 新增\",TEXT)")&&ui.contains("panel.addView(labeled(\"名称\",name))")&&ui.contains("panel.addView(labeled(\"协议\",protocol))")&&ui.contains("panel.addView(labeled(\"API Base URL\",endpoint))")&&ui.contains("panel.addView(labeled(\"默认模型\",model))"),"every API record is a plain editable one and custom profiles remain unlimited");
        System.out.println("ApiProfileStructureTest PASS");
    }
}
