package com.orangemall.api.client;

import com.orangemall.api.dto.AddressDTO;
import com.orangemall.api.dto.PointsChangeReq;
import com.orangemall.api.dto.UserBrief;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "mall-user", contextId = "userInner", path = "/inner")
public interface UserInnerClient {
    /** 不存在抛 9006 */
    @GetMapping("/users/{id}")
    Result<UserBrief> user(@PathVariable("id") Long id);

    /** 默认地址在前 */
    @GetMapping("/users/{id}/addresses")
    Result<List<AddressDTO>> addresses(@PathVariable("id") Long id);

    /** 积分变动（Seata AT 分支），返回变动后余额；扣减时余额不足抛 8003 */
    @PostMapping("/points/change")
    Result<Integer> changePoints(@RequestBody PointsChangeReq req);
}
