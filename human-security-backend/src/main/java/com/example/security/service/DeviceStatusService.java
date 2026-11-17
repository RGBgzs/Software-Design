package com.example.security.service;

import com.example.security.entity.DeviceStatus;
import com.example.security.mapper.DeviceStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceStatusService {

    @Autowired
    private DeviceStatusMapper deviceStatusMapper;

    /**
     * 查询所有设备状态
     */
    public List<DeviceStatus> getAll() {
        return deviceStatusMapper.selectList(null);
    }

    /**
     * 生成设备状态文本摘要，供问答接口使用。
     * 输出格式为每设备一行，便于在聊天窗口中阅读。
     */
    public String buildStatusSummary(List<DeviceStatus> devices) {
        if (devices == null || devices.isEmpty()) {
            return "当前没有已注册的设备。";
        }

        int onlineCount = 0;
        int defenseCount = 0;
        int totalCount = devices.size();
        StringBuilder sb = new StringBuilder();
        sb.append("当前设备状态如下：\n\n");

        for (int i = 0; i < devices.size(); i++) {
            DeviceStatus d = devices.get(i);
            String onlineText = (d.getOnline() != null && d.getOnline() == 1) ? "在线" : "离线";
            String defenseText = (d.getDefense() != null && d.getDefense() == 1) ? "已布防" : "已撤防";
            String batteryText = (d.getBattery() != null) ? d.getBattery() + "%" : "N/A";
            String lastTime = (d.getLastReportTime() != null)
                    ? d.getLastReportTime().toString().replace("T", " ")
                    : "无记录";

            sb.append(i + 1).append(". ")
              .append(d.getDeviceName())
              .append("：").append(onlineText)
              .append("，位置：").append(d.getLocation())
              .append("，电量：").append(batteryText)
              .append("，状态：").append(defenseText)
              .append("，最后上报时间：").append(lastTime)
              .append("\n");

            if (d.getOnline() != null && d.getOnline() == 1) {
                onlineCount++;
            }
            if (d.getDefense() != null && d.getDefense() == 1) {
                defenseCount++;
            }
        }

        // 系统判断
        sb.append("\n系统判断：");
        if (onlineCount == totalCount) {
            sb.append("核心安防设备全部在线");
        } else if (onlineCount > 0) {
            sb.append(onlineCount + "/" + totalCount + " 台设备在线，有 " + (totalCount - onlineCount) + " 台离线");
        } else {
            sb.append("所有设备均离线，请立即检查！");
        }

        if (defenseCount == totalCount) {
            sb.append("，处于布防状态，可以正常进行安防监测。");
        } else if (defenseCount > 0) {
            sb.append("，部分设备已布防，注意未布防区域。");
        } else {
            sb.append("，所有设备均已撤防，建议根据需要重新布防。");
        }

        return sb.toString();
    }
}
