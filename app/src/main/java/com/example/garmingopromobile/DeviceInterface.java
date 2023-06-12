package com.example.garmingopromobile;

public class DeviceInterface {
    private GoPro goPro;
    private GarminDevice watch;

    public DeviceInterface() {
    }


    public GoPro getGoPro() {
        return goPro;
    }

    public void setGoPro(GoPro goPro) {
        this.goPro = goPro;
        this.goPro.setLinkedWatch(watch);
        if (this.watch != null) {
            watch.setLinkedGoPro(goPro);
        }
    }


    public GarminDevice getWatch() {
        return watch;
    }

    public void setWatch(GarminDevice watch) {
        this.watch = watch;
        this.watch.setLinkedGoPro(goPro);
        if (this.goPro != null) {
            goPro.setLinkedWatch(watch);
        }
    }
}
