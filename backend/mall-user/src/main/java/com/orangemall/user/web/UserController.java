package com.orangemall.user.web;

import com.orangemall.common.Result;
import com.orangemall.user.dto.Dtos.AddressVO;
import com.orangemall.user.dto.Dtos.UserProfile;
import com.orangemall.user.service.AddressService;
import com.orangemall.user.service.AuthService;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final AuthService auth;
    private final AddressService addresses;

    @GetMapping("/users/me")
    public Result<UserProfile> me() {
        return Result.ok(UserProfile.of(auth.requireUser(UserContext.requireUser())));
    }

    @GetMapping("/addresses")
    public Result<List<AddressVO>> addresses() {
        return Result.ok(addresses.list(UserContext.requireUser()).stream().map(AddressVO::of).toList());
    }

    @PostMapping("/addresses")
    public Result<AddressVO> save(@RequestBody AddressVO body) {
        return Result.ok(addresses.save(UserContext.requireUser(), body));
    }

    @DeleteMapping("/addresses/{id}")
    public Result<Boolean> delete(@PathVariable("id") long id) {
        addresses.delete(UserContext.requireUser(), id);
        return Result.ok();
    }
}
