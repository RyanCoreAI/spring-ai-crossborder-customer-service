package com.omnimerchant.channel;

public record ChannelCustomerIdentity(
        String identityType,
        String identityValue,
        String displayValueMasked,
        boolean verified) {
}
