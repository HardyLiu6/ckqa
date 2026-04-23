package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.MaterialObjects;
import org.ysu.ckqaback.mapper.MaterialObjectsMapper;
import org.ysu.ckqaback.service.MaterialObjectsService;

/**
 * <p>
 * 资料对象表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-23
 */
@Service
public class MaterialObjectsServiceImpl extends ServiceImpl<MaterialObjectsMapper, MaterialObjects>
        implements MaterialObjectsService {

}
