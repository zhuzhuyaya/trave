package com.travel.diary.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20, message = "昵称需在 1-20 个字之间")
    private String nickname;

    @Size(max = 255, message = "个性签名最多 255 个字")
    private String bio;

    @Size(max = 255, message = "头像地址太长了")
    private String avatar;
}
