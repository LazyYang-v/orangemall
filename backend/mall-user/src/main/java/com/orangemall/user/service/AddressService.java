package com.orangemall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.common.IdGenerator;
import com.orangemall.user.dto.Dtos.AddressVO;
import com.orangemall.user.entity.Address;
import com.orangemall.user.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final AddressMapper mapper;

    /** 按创建顺序（与 Mock 的 push 顺序一致） */
    public List<Address> list(long userId) {
        return mapper.selectList(Wrappers.<Address>lambdaQuery().eq(Address::getUserId, userId)
                .orderByAsc(Address::getCreatedAt).orderByAsc(Address::getId));
    }

    /** 内部接口：默认地址在前 */
    public List<Address> listDefaultFirst(long userId) {
        return list(userId).stream()
                .sorted(Comparator.comparing((Address a) -> !Boolean.TRUE.equals(a.getIsDefault())))
                .toList();
    }

    /** 有 id 且属于当前用户为更新，否则新增；isDefault=true 或首个地址时设为默认并清除其余默认 */
    @Transactional(rollbackFor = Exception.class)
    public AddressVO save(long userId, AddressVO body) {
        List<Address> list = list(userId);
        Address existing = body.id() == null ? null
                : list.stream().filter(a -> a.getId().equals(body.id())).findFirst().orElse(null);
        boolean makeDefault = Boolean.TRUE.equals(body.isDefault()) || list.isEmpty();
        if (makeDefault) mapper.clearDefault(userId);
        Address a = existing != null ? existing : new Address();
        if (existing == null) {
            a.setId(IdGenerator.nextId());
            a.setUserId(userId);
        }
        a.setReceiver(pick(body.receiver(), a.getReceiver()));
        a.setPhone(pick(body.phone(), a.getPhone()));
        a.setProvince(pick(body.province(), a.getProvince()));
        a.setCity(pick(body.city(), a.getCity()));
        a.setDistrict(pick(body.district(), a.getDistrict()));
        a.setDetail(pick(body.detail(), a.getDetail()));
        if (makeDefault) a.setIsDefault(true);
        else if (body.isDefault() != null) a.setIsDefault(false);
        else if (a.getIsDefault() == null) a.setIsDefault(false);
        if (existing == null) mapper.insert(a);
        else mapper.updateById(a);
        return AddressVO.of(a);
    }

    public void delete(long userId, long id) {
        mapper.delete(Wrappers.<Address>lambdaQuery().eq(Address::getId, id).eq(Address::getUserId, userId));
    }

    private static String pick(String v, String old) {
        if (v != null) return v;
        return old == null ? "" : old;
    }
}
