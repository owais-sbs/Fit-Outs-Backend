package com.fitouts.communications.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChannelRequest {

    private String channelType;
    private String name;
    private List<Long> memberAccountIds;
}
