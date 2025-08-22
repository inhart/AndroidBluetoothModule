package com.kingdom13.mylibrary;


import android.util.Log;

public class mylibclass {

    public static String getMessage() {
        Log.d("MyUnityModule", "El módulo se cargó correctamente desde Unity.");
        return "Hola desde el módulo Android!";
    }

}

