package com.example.dentalclinicappointmentapp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// The visit types and dentists patients can choose from.
// They mirror the defaults on the admin website (script.js). The website keeps its own lists
// in the browser and saves only the plain names on each appointment, so the spelling here
// must match the website exactly or the two sides will not recognise each other's bookings.
public final class Catalog {

    public static final class Service {
        public final String name;
        public final String description;

        private Service(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static final class Dentist {
        public final int id;
        public final String name;
        public final String specialty;

        private Dentist(int id, String name, String specialty) {
            this.id = id;
            this.name = name;
            this.specialty = specialty;
        }
    }

    public static final List<Service> SERVICES = Collections.unmodifiableList(Arrays.asList(
            new Service("Check-up", "General dental check-up and consultation."),
            new Service("Cleaning", "Professional dental cleaning and scaling."),
            new Service("Filling", "Tooth restoration and dental filling service."),
            new Service("Braces", "Orthodontic consultation and adjustment."),
            new Service("General Consultation", "General appointment and consultation service.")));

    public static final List<Dentist> DENTISTS = Collections.unmodifiableList(Arrays.asList(
            new Dentist(1, "Praswel Jim Dimaano", "General Dentistry"),
            new Dentist(2, "Clemence Valdez", "Dental Surgery"),
            new Dentist(3, "John Benedict Centeno", "Orthodontics")));

    private Catalog() {}
}