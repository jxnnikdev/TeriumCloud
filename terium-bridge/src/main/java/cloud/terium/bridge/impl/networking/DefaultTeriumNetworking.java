package cloud.terium.bridge.impl.networking;

import cloud.terium.bridge.impl.config.ConfigManager;
import cloud.terium.networking.TeriumFramework;
import cloud.terium.networking.client.TeriumClient;
import cloud.terium.networking.packet.Packet;
import lombok.Getter;

@Getter
public class DefaultTeriumNetworking {

    private final TeriumClient client;
    private final ConfigManager configManager;

    public DefaultTeriumNetworking(ConfigManager configManager) {
        this.configManager = configManager;

        System.out.println("[TeriumBridge/DefaultTeriumNetworking] Trying to start terium-client...");
        this.client = TeriumFramework.createClient(configManager.getString("ip"), configManager.getInt("port"));
        System.out.println("[TeriumBridge/DefaultTeriumNetworking] Successfully started terium-client...");
    }

    public void sendPacket(Packet packet) {
        this.client.getChannel().writeAndFlush(packet);
        System.out.println("Sending packet: " + packet.getClass().getSimpleName());
    }
}