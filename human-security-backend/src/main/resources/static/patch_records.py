with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('\r\n', '\n')

old = '''    h += '<tr><td>' + (r.id || "-") + '</td><td>' + matchedBadge + '</td><td>' + esc(r.personName || "-") + '</td><td>' + esc(r.personType || "-") + '</td><td>' + formatSimilarity(r.similarity) + '</td><td>' + riskBadge + '</td><td>' + esc(r.deviceCode || "-") + '</td><td>' + esc(loc) + '</td><td>' + (r.alarmId || "-") + '</td><td style="white-space:nowrap">' + ft(r.createdAt || r.captureTime) + '</td><td>' + (r.alarmId ? '<button class="btn btn-d btn-xs" onclick="goFaceAlarm(' + r.alarmId + ')">\u{1f6a8} 查看告警</button>' : '<span style="color:#94a3b8;font-size:11px">无</span>') + '</td></tr>';'''

# Try with the actual emoji
idx_start = content.find("h += '<tr><td>' + (r.id || ")
if idx_start > 0:
    idx_end = content.find(";\n", idx_start)
    old_block = content[idx_start:idx_end]
    print(f"Found old block at {idx_start}, length {len(old_block)}")

    new_block = '''h += '<tr><td>' + (r.id || "-") + '</td><td>' + matchedBadge + '</td><td>' + esc(r.personName || "-") + '</td><td>' + esc(r.personType || "-") + '</td><td>' + formatSimilarity(r.similarity) + '</td><td>' + faceRiskBadge(r.riskLevel) + '</td><td>' + esc(r.deviceCode || "-") + '</td><td>' + esc(loc) + '</td><td>' + (r.alarmId != null ? r.alarmId : "-") + '</td><td style="white-space:nowrap">' + ft(r.createdAt || r.captureTime) + '</td><td>' + (r.alarmId ? '<button class="btn btn-d btn-xs" onclick="goFaceAlarm(' + r.alarmId + ')">\u{1f6a8} 查看告警</button>' : '<span style="color:#94a3b8;font-size:11px">暂无</span>') + '</td></tr>''''

    content = content[:idx_start] + new_block + content[idx_end:]

    with open('index.html', 'w', encoding='utf-8', newline='') as f:
        f.write(content)
    print("FIX records: OK")
else:
    print("NOT FOUND")
