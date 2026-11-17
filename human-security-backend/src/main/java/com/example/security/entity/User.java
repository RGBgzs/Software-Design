package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    @TableField("real_name")
    private String realName;
    private String role;
    @TableField("guard_id")
    private Long guardId;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;

    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public String getUsername(){return username;}
    public void setUsername(String u){this.username=u;}
    public String getPassword(){return password;}
    public void setPassword(String p){this.password=p;}
    public String getRealName(){return realName;}
    public void setRealName(String r){this.realName=r;}
    public String getRole(){return role;}
    public void setRole(String r){this.role=r;}
    public Long getGuardId(){return guardId;}
    public void setGuardId(Long g){this.guardId=g;}
    public String getPhone(){return phone;}
    public void setPhone(String p){this.phone=p;}
    public Integer getStatus(){return status;}
    public void setStatus(Integer s){this.status=s;}
    public LocalDateTime getCreateTime(){return createTime;}
    public void setCreateTime(LocalDateTime t){this.createTime=t;}
}
