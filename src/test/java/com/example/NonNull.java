package com.example;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface NonNull {}
