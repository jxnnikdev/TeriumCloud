package cloud.terium.plugin.impl.service;

import cloud.terium.teriumapi.service.ICloudService;
import cloud.terium.teriumapi.service.ICloudServiceProvider;
import cloud.terium.teriumapi.service.ServiceType;

import java.security.Provider;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class ServiceProvider implements ICloudServiceProvider {

    private final List<ICloudService> cachedServices;

    public ServiceProvider() {
        this.cachedServices = new LinkedList<>();
    }

    @Override
    public Optional<ICloudService> getCloudServiceByName(String serviceName) {
        return cachedServices.stream().filter(cloudService -> cloudService.getServiceName().equals(serviceName)).findAny();
    }

    @Override
    public List<ICloudService> getCloudServicesByGroupName(String serviceGroup) {
        return cachedServices.stream().filter(cloudService -> cloudService.getServiceGroup().getGroupName().equals(serviceGroup)).toList();
    }

    @Override
    public List<ICloudService> getCloudServicesByGroupTitle(String groupTitle) {
        return cachedServices.stream().filter(cloudService -> cloudService.getServiceGroup().getGroupTitle().equals(groupTitle)).toList();
    }

    @Override
    public List<ICloudService> getAllLobbyServices() {
        return cachedServices.stream().filter(cloudService -> cloudService.getServiceType().equals(ServiceType.Lobby)).toList();
    }

    @Override
    public List<ICloudService> getAllCloudServices() {
        return cachedServices;
    }
}
