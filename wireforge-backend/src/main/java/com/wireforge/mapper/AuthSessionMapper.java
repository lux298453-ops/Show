package com.wireforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wireforge.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}
