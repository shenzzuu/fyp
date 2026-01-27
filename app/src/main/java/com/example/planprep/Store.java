package com.example.planprep;

public class Store {
    private String name;
    private String address;
    private String url;
    private String distance;    // Display string (e.g., "1.2 km")
    private double distanceVal; // Numeric value for sorting logic

    public Store(String name, String address, String url, String distance, double distanceVal) {
        this.name = name;
        this.address = address;
        this.url = url;
        this.distance = distance;
        this.distanceVal = distanceVal;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getUrl() { return url; }
    public String getDistance() { return distance; }

    // Getter for sorting
    public double getDistanceVal() { return distanceVal; }
}