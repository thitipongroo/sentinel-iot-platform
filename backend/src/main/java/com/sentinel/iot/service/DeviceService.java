package com.sentinel.iot.service;

import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RedisService redisService;

    public Device create(DeviceRequest req) {
        if (deviceRepository.existsByName(req.getName())) {
            throw new IllegalArgumentException("Device name already exists: " + req.getName());
        }
        Device device = new Device();
        device.setName(req.getName());
        device.setDescription(req.getDescription());
        device.setLocation(req.getLocation());
        device.setStatus("OFFLINE");
        Device saved = deviceRepository.save(device);
        redisService.setDeviceStatus(saved.getId().toString(), "OFFLINE");
        return saved;
    }

    public List<Device> findAll() {
        List<Device> devices = deviceRepository.findAll();
        devices.forEach(d -> {
            String cachedStatus = redisService.getDeviceStatus(d.getId().toString());
            if (cachedStatus != null) {
                d.setStatus(cachedStatus);
            }
        });
        return devices;
    }

    public Device findById(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + id));
        String cachedStatus = redisService.getDeviceStatus(id.toString());
        if (cachedStatus != null) {
            device.setStatus(cachedStatus);
        }
        return device;
    }

    public Device updateStatus(UUID id, String status) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + id));
        device.setStatus(status);
        redisService.setDeviceStatus(id.toString(), status);
        return deviceRepository.save(device);
    }
}
