package cloud.terium.networking.packet;

import cloud.terium.teriumapi.console.LogType;
import cloud.terium.teriumapi.network.Packet;

public record PacketPlayOutSendConsole(String message, LogType logType) implements Packet {
}