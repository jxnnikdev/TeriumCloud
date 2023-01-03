package cloud.terium.cloudsystem.service;

import cloud.terium.cloudsystem.TeriumCloud;
import cloud.terium.cloudsystem.event.events.service.ServiceUpdateEvent;
import cloud.terium.cloudsystem.utils.logger.Logger;
import cloud.terium.networking.packet.service.PacketPlayOutServiceRemove;
import cloud.terium.teriumapi.console.LogType;
import cloud.terium.teriumapi.service.ICloudService;
import cloud.terium.teriumapi.service.ServiceState;
import cloud.terium.teriumapi.service.ServiceType;
import cloud.terium.teriumapi.service.group.ICloudServiceGroup;
import cloud.terium.teriumapi.template.ITemplate;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class CloudService implements ICloudService {

    private final ICloudServiceGroup serviceGroup;
    private ServiceState serviceState;
    private ServiceType serviceType;
    private final String name;
    private final File folder;
    private final int port;
    private final int serviceId;
    private final int maxPlayers;
    private final int maxMemory;
    private long usedMemory;
    private int onlinePlayers;
    private boolean locked;
    private Process process;
    private final List<ITemplate> templates;
    private final HashMap<String, Object> propertyMap;
    private Thread outputThread;
    private Thread thread;

    public CloudService(ICloudServiceGroup cloudServiceGroup) {
        this(cloudServiceGroup.getTemplates(), cloudServiceGroup, TeriumCloud.getTerium().getServiceProvider().getFreeServiceId(cloudServiceGroup), cloudServiceGroup.getPort());
    }

    public CloudService(List<ITemplate> templates, ICloudServiceGroup cloudServiceGroup, int serviceId, int port) {
        this(templates, cloudServiceGroup, serviceId, port, cloudServiceGroup.getMaxPlayers());
    }

    public CloudService(List<ITemplate> templates, ICloudServiceGroup cloudServiceGroup, int serviceId, int port, int maxPlayers) {
        this(cloudServiceGroup.getGroupName(), templates, cloudServiceGroup, cloudServiceGroup.getServiceType(), serviceId, port, maxPlayers, cloudServiceGroup.getMemory());
    }

    public CloudService(String serviceName, List<ITemplate> templates, ICloudServiceGroup cloudServiceGroup, ServiceType serviceType, int serviceId, int port, int maxPlayers, int maxMemory) {
        this.serviceGroup = cloudServiceGroup;
        this.serviceId = serviceId;
        this.name = serviceName;
        this.serviceType = serviceType;
        this.serviceState = ServiceState.PREPARING;
        this.templates = templates;
        this.folder = new File("servers//" + getServiceName());
        this.propertyMap = new HashMap<>();
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.maxMemory = maxMemory;
        this.usedMemory = 0;
        this.onlinePlayers = 0;
        //TODO: Write screen system | TeriumCloud.getTerium().getScreenManager().addCloudService(this);
        TeriumCloud.getTerium().getServiceProvider().addService(this);
        Logger.log("Successfully created service " + getServiceName() + ".", LogType.INFO);
    }

    @SneakyThrows
    public void start() {
        this.folder.mkdirs();
        FileUtils.copyFileToDirectory(new File("data//versions//" + (serviceGroup.getServiceType() == ServiceType.Lobby || serviceGroup.getServiceType() == ServiceType.Server ? "server.jar" : "velocity.jar")), folder);
        FileUtils.copyFileToDirectory(new File("data//versions//terium-bridge//terium-config.json"), folder);
        FileUtils.copyDirectory(new File(serviceGroup.getServiceType() == ServiceType.Lobby || serviceGroup.getServiceType() == ServiceType.Server ? "templates//Global//server" : "templates//Global//proxy"), folder);
        FileUtils.copyFileToDirectory(new File("data//versions//teriumbridge.jar"), new File("servers//" + getServiceName() + "//plugins//"));
        templates.forEach(template -> {
            try {
                FileUtils.copyDirectory(template.getPath().toFile(), folder);
            } catch (IOException ignored) {}
        });

        if (serviceGroup.getServiceType() == ServiceType.Lobby || serviceGroup.getServiceType() == ServiceType.Server) {
            Logger.log("The service '" + getServiceName() + "' is starting on port " + port + ".", LogType.INFO);

            Properties properties = new Properties();
            File serverProperties = new File(this.folder, "server.properties");
            properties.setProperty("server-name", getServiceName());
            properties.setProperty("server-port", getPort() + "");
            properties.setProperty("server-ip", "127.0.0.1");
            properties.setProperty("online-mode", "false");

            try (OutputStream outputStream = new FileOutputStream(serverProperties);
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {

                properties.store(writer, null);
            }

            properties = new Properties();
            File eula = new File(this.folder, "eula.txt");

            eula.createNewFile();
            properties.setProperty("eula", "true");

            try (OutputStream outputStream = new FileOutputStream(eula)) {
                properties.store(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), "Auto eula agreement by TeriumCloud.");
            }
        } else {
            Logger.log("The service '" + getServiceName() + "' is starting on port " + port + ".", LogType.INFO);
            this.replaceInFile(new File(this.folder, "velocity.toml"), "%name%", getServiceName());
            this.replaceInFile(new File(this.folder, "velocity.toml"), "%port%", port + "");
            this.replaceInFile(new File(this.folder, "velocity.toml"), "%max_players%", serviceGroup.getMaxPlayers() + "");
        }

        if (!serviceGroup.getServiceType().equals(ServiceType.Proxy))
            //TODO: Looking with events | TeriumCloud.getTerium().getNetworking().sendPacket(new PacketPlayOutServiceAdd(this, serviceGroup, templates, getServiceId(), getPort()));

        this.thread = new Thread(() -> {
            String[] command = new String[]{"java", "-jar", "-Xmx" + serviceGroup.getMemory() + "m", serviceGroup.getServiceType() == ServiceType.Lobby || serviceGroup.getServiceType() == ServiceType.Server ? "server.jar" : "velocity.jar", "nogui"};
            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.directory(this.folder);
            try {
                this.process = processBuilder.start();
            } catch (IOException exception) {
                exception.printStackTrace();
            }

            int resultCode = 0;
            try {
                resultCode = this.process.waitFor();
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            }

            TeriumCloud.getTerium().getServiceProvider().removeService(this);
            TeriumCloud.getTerium().getNetworking().sendPacket(new PacketPlayOutServiceRemove(this));
            try {
                FileUtils.deleteDirectory(this.folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.log("Successfully stopped service '" + getServiceName() + "'.", LogType.INFO);
        });
        this.thread.start();
    }

    public void shutdown() {
        CloudService cloudService = this;
        // Todo: Screen 
        /*if (TeriumCloud.getTerium().getCloudUtils().isInScreen() && TeriumCloud.getTerium().getScreenManager().getCurrentScreen().equals(this))
            toggleScreen();*/
        Logger.log("Trying to stop service '" + getServiceName() + "'... [CloudService#shutdown]", LogType.INFO);
        if (!serviceGroup.getServiceType().equals(ServiceType.Proxy))
            TeriumCloud.getTerium().getNetworking().sendPacket(new PacketPlayOutServiceRemove(this));

        thread.stop();
        process.destroyForcibly();
        delete();
        Logger.log("Successfully stopped service '" + getServiceName() + "'.", LogType.INFO);
    }

    public void restart() {
        CloudService cloudService = this;
        // Todo: Screen 
        /*if (TeriumCloud.getTerium().getCloudUtils().isInScreen() && TeriumCloud.getTerium().getScreenManager().getCurrentScreen().equals(this))
            toggleScreen();*/
        Logger.log("Trying to stop service '" + getServiceName() + "'... [CloudService#shutdown]", LogType.INFO);
        if (!serviceGroup.getServiceType().equals(ServiceType.Proxy))
            TeriumCloud.getTerium().getNetworking().sendPacket(new PacketPlayOutServiceRemove(this));

        setOnlinePlayers(0);
        setUsedMemory(0);
        setServiceState(ServiceState.PREPARING);
        thread.stop();
        process.destroyForcibly();
        Logger.log("Successfully stopped service '" + getServiceName() + "'.", LogType.INFO);
        start();
    }

    @SneakyThrows
    public void delete() {
        FileUtils.deleteDirectory(folder);
        TeriumCloud.getTerium().getServiceProvider().removeService(this);
    }

    /*@SneakyThrows
    @Override
    public void forceShutdown() {
        MinecraftService minecraftService = this;
        Logger.log("Trying to stop service '" + getServiceName() + "'. [MinecraftService#forceShutdown]", LogType.INFO);
        if (!serviceGroup.getServiceType().equals(ServiceType.Proxy))
            TeriumCloud.getTerium().getDefaultTeriumNetworking().sendPacket(new PacketPlayOutServiceRemove(getServiceName()));
        thread.stop();
        process.destroyForcibly();
        new Timer().schedule(new TimerTask() {
            @SneakyThrows
            @Override
            public void run() {
                FileUtils.deleteDirectory(folder);
                TeriumCloud.getTerium().getServiceManager().removeService(minecraftService);
                Logger.log("Successfully stopped service '" + getServiceName() + "'.", LogType.INFO);
            }
        }, 5000);
    }*/

    // Todo: Screen
    /*public void toggleScreen() {
        if (!TeriumCloud.getTerium().getCloudUtils().isInScreen()) {
            outputThread = new Thread(() -> {
                String line = null;
                BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while (true) {
                    try {
                        if ((line = input.readLine()) == null) break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Logger.log(line, LogType.SCREEN);
                    TeriumCloud.getTerium().getScreenManager().addLogToScreen(this, "[" + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()) + "\u001B[0m] " + LogType.SCREEN.getPrefix() + line);
                }
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            if (TeriumCloud.getTerium().getScreenManager().getLogsFromService(this) != null) {
                TeriumCloud.getTerium().getScreenManager().getLogsFromService(this).forEach(log -> Logger.log(log, LogType.SCREEN));
            }
            Logger.log("You're now inside of " + getServiceName() + ".", LogType.INFO);
            TeriumCloud.getTerium().getCloudUtils().setInScreen(true);
            TeriumCloud.getTerium().getScreenManager().setCurrentScreen(this);
            outputThread.start();
        } else {
            outputThread.stop();
            TeriumCloud.getTerium().getScreenManager().setCurrentScreen(null);
            TeriumCloud.getTerium().getCloudUtils().setInScreen(false);
            Logger.log("You left the screen from " + getServiceName() + ".", LogType.INFO);
            Logger.logAllCachedLogs();
        }
    }*/

    private void replaceInFile(File file, String placeHolder, String replacedWith) {
        String content;
        try {
            final Path path = file.toPath();

            content = Files.readString(path);
            content = content.replace(placeHolder, replacedWith);

            Files.writeString(path, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getServiceName() {
        return getServiceId() > 9 ? name + "-" + getServiceId() : name + "-0" + getServiceId();
    }

    @Override
    public int getServiceId() {
        return serviceId;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public List<ITemplate> getTemplates() {
        return templates;
    }

    @Override
    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    @Override
    public void setOnlinePlayers(int onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    @Override
    public long getUsedMemory() {
        return usedMemory;
    }

    @Override
    public void setUsedMemory(long usedMemory) {
        this.usedMemory = usedMemory;
    }

    @Override
    public void update() {
        TeriumCloud.getTerium().getEventProvider().callEvent(new ServiceUpdateEvent(this, serviceState, locked, usedMemory, onlinePlayers));
    }

    @Override
    public ICloudServiceGroup getServiceGroup() {
        return serviceGroup;
    }

    @Override
    public ServiceState getServiceState() {
        return serviceState;
    }

    @Override
    public void setServiceState(ServiceState serviceState) {
        this.serviceState = serviceState;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void addProperty(String name, Object property) {
        this.propertyMap.put(name, property);
    }

    @Override
    public void removeProperty(String name) {
        this.propertyMap.remove(name);
    }

    @Override
    public Object getProperty(String name) {
        return propertyMap.get(name);
    }
}