package io.core;

import androidx.annotation.NonNull;

public class InvalidRecord extends Exception{
    public InvalidRecord(@NonNull String info){
        super(info);
    }
}
