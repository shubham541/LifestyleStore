package com.shubham.lifestylestore.service;

/** Store-wide pricing rules, applied on the server only. Amounts are in paise. */
public final class Pricing {

    public static final long FREE_SHIPPING_ABOVE_PAISE = 499_00;
    public static final long SHIPPING_FEE_PAISE = 49_00;

    private Pricing() {
    }

    public static long shippingFor(long subtotalPaise) {
        return subtotalPaise == 0 || subtotalPaise >= FREE_SHIPPING_ABOVE_PAISE ? 0 : SHIPPING_FEE_PAISE;
    }
}
