package com.genersoft.iot.vmp.gb28181.session;

import com.genersoft.iot.vmp.gb28181.bean.CatalogData;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.bean.SyncStatus;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.vmp.storager.IVideoManagerStorage;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CatalogDataCatch {

    public static Map<String, CatalogData> data = new ConcurrentHashMap<>();

    @Autowired
    private DeferredResultHolder deferredResultHolder;

    @Autowired
    private IVideoManagerStorage storager;

    public void addReady(Device device, int sn ) {
        CatalogData catalogData = data.get(device.getDeviceId());
        if (catalogData == null || catalogData.getStatus().equals(CatalogData.CatalogDataStatus.end)) {
            catalogData = new CatalogData();
            catalogData.setChannelList(new ArrayList<>());
            catalogData.setDevice(device);
            catalogData.setSn(sn);
            catalogData.setStatus(CatalogData.CatalogDataStatus.ready);
            catalogData.setLastTime(new Date(System.currentTimeMillis()));
            data.put(device.getDeviceId(), catalogData);
        }
    }

    public void put(String deviceId, int sn, int total, Device device, List<DeviceChannel> deviceChannelList) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null) {
            catalogData = new CatalogData();
            catalogData.setSn(sn);
            catalogData.setTotal(total);
            catalogData.setDevice(device);
            catalogData.setChannelList(new ArrayList<>());
            catalogData.setStatus(CatalogData.CatalogDataStatus.runIng);
            catalogData.setLastTime(new Date(System.currentTimeMillis()));
            data.put(deviceId, catalogData);
        }else {
            // 同一个设备的通道同步请求只考虑一个，其他的直接忽略
            if (catalogData.getSn() != sn) {
                return;
            }
            catalogData.setTotal(total);
            catalogData.setDevice(device);
            catalogData.setStatus(CatalogData.CatalogDataStatus.runIng);
            catalogData.getChannelList().addAll(deviceChannelList);
            catalogData.setLastTime(new Date(System.currentTimeMillis()));
        }
    }

    public List<DeviceChannel> get(String deviceId) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null) return null;
        return catalogData.getChannelList();
    }

    public int getTotal(String deviceId) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null) return 0;
        return catalogData.getTotal();
    }

    public SyncStatus getSyncStatus(String deviceId) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null) return null;
        SyncStatus syncStatus = new SyncStatus();
        syncStatus.setCurrent(catalogData.getChannelList().size());
        syncStatus.setTotal(catalogData.getTotal());
        syncStatus.setErrorMsg(catalogData.getErrorMsg());
        return syncStatus;
    }

    public boolean isSyncRunning(String deviceId) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null) return false;
        return !catalogData.getStatus().equals(CatalogData.CatalogDataStatus.end);
    }

    @Scheduled(fixedRate = 5 * 1000)   //每5秒执行一次, 发现数据5秒未更新则移除数据并认为数据接收超时
    private void timerTask(){
        Set<String> keys = data.keySet();
        Calendar calendarBefore5S = Calendar.getInstance();
        calendarBefore5S.setTime(new Date());
        calendarBefore5S.set(Calendar.SECOND, calendarBefore5S.get(Calendar.SECOND) - 5);

        Calendar calendarBefore30S = Calendar.getInstance();
        calendarBefore30S.setTime(new Date());
        calendarBefore30S.set(Calendar.SECOND, calendarBefore30S.get(Calendar.SECOND) - 30);
        for (String deviceId : keys) {
            CatalogData catalogData = data.get(deviceId);
            if ( catalogData.getLastTime().before(calendarBefore5S.getTime())) { // 超过五秒收不到消息任务超时， 只更新这一部分数据
                if (catalogData.getStatus().equals(CatalogData.CatalogDataStatus.runIng)) {
                    storager.resetChannels(catalogData.getDevice().getDeviceId(), catalogData.getChannelList());
                    if (catalogData.getTotal() != catalogData.getChannelList().size()) {
                        String errorMsg = "更新成功，共" + catalogData.getTotal() + "条，已更新" + catalogData.getChannelList().size() + "条";
                        catalogData.setErrorMsg(errorMsg);
                    }
                }else if (catalogData.getStatus().equals(CatalogData.CatalogDataStatus.ready)) {
                    String errorMsg = "同步失败，等待回复超时";
                    catalogData.setErrorMsg(errorMsg);
                }
                catalogData.setStatus(CatalogData.CatalogDataStatus.end);
            }
            if (catalogData.getStatus().equals(CatalogData.CatalogDataStatus.end) && catalogData.getLastTime().before(calendarBefore30S.getTime())) { // 超过三十秒，如果标记为end则删除
                data.remove(deviceId);
            }
        }
    }


    public void setChannelSyncEnd(String deviceId, String errorMsg) {
        CatalogData catalogData = data.get(deviceId);
        if (catalogData == null)return;
        catalogData.setStatus(CatalogData.CatalogDataStatus.end);
        catalogData.setErrorMsg(errorMsg);
    }
}
