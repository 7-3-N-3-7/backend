package com.platform.config;

import com.platform.entity.I18nDictionary;
import com.platform.repository.I18nDictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("!test")
public class ClinicDatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClinicDatabaseInitializer.class);

    private final I18nDictionaryRepository i18nDictionaryRepository;

    public ClinicDatabaseInitializer(I18nDictionaryRepository i18nDictionaryRepository) {
        this.i18nDictionaryRepository = i18nDictionaryRepository;
    }

    @Override
    public void run(String... args) {
        seedI18nDictionaries();
    }

    private void seedI18nDictionaries() {
        String[] locales = {"en", "da", "fr", "es", "de"};
        for (String loc : locales) {
            try {
                I18nDictionary dict = i18nDictionaryRepository.findByLocale(loc)
                        .orElseGet(() -> new I18nDictionary(loc, new HashMap<>()));
                Map<String, Object> root = dict.getTranslations();
                if (root == null) {
                    root = new HashMap<>();
                }
                root.put("portal", buildPortalTranslations(loc));
                dict.setTranslations(root);
                i18nDictionaryRepository.save(dict);
            } catch (Exception e) {
                log.warn("Could not seed MongoDB i18n dictionary for {}: {}", loc, e.getMessage());
            }
        }
        log.info("MongoDB i18n_dictionaries enriched with portal translations for en, da, fr, es, de.");
    }

    private Map<String, Object> buildPortalTranslations(String loc) {
        Map<String, Object> p = new LinkedHashMap<>();
        switch (loc) {
            case "da" -> {
                p.put("brandTitle", "EduJournal Klinik");
                p.put("therapistPortalSub", "Terapeut & Klinikportal");
                p.put("adminPortalSub", "Platform Administration (i18n)");
                p.put("clientPortalSub", "Personlig Klientportal");
                p.put("navClientOverview", "Mit Overblik, Journal & Booking");
                p.put("navClientSettings", "Mine Stamdata");
                p.put("navTherapistCalendar", "Klinik Kalender & Klient-Workspaces");
                p.put("navTherapistSettings", "Klinik CVR & Arbejdstid");
                p.put("dbLiveBadge", "PostgreSQL & JGit Live");
                p.put("activeWorkspace", "Aktivt Workspace");
                p.put("therapistClinicProfile", "Terapeut Klinik-Profil");
                p.put("personalClientProfile", "Personlig Klient-Profil");
                p.put("switchWorkspaceHeader", "Skift Workspace / Profil");
                p.put("switchToTherapist", "Skift til Terapeut Klinik-Profil");
                p.put("switchToClient", "Skift til Klient-Profil");
                p.put("registerAsTherapistBtn", "Registrer som Terapeut (CVR)");
                p.put("adminI18nBtn", "Admin Sprog- & i18n Styring");
                p.put("profileSettingsBtn", "Min Profil & Indstillinger");
                p.put("signOutBtn", "Log ud");
                p.put("welcomeBack", "Velkommen tilbage, {name}");
                p.put("nextScheduledSession", "Næste Planlagte Session");
                p.put("noUpcomingAppointments", "Ingen kommende aftaler");
                p.put("latestMoodScore", "Seneste Humør-score");
                p.put("therapistConsentCard", "Behandler-Samtykke");
                p.put("activeTherapistsCount", "{count} Aktiv(e) Terapeut(er)");
                p.put("pcl5DrawerBtn", "PCL-5 Selvevaluering");
                p.put("bookNewSessionBtn", "Book Ny Samtale");
                p.put("tabJournal", "Min Dagbog & PCL-5");
                p.put("tabAppointments", "Mine Aftaler ({count})");
                p.put("tabMarketplace", "Find Behandler & Book");
                p.put("tabConsent", "Behandler-Adgang ({count})");
                p.put("therapistCalendarTitle", "Klinik Kalender & Multi-Window Workspaces");
                p.put("newAppointmentBtn", "Ny Aftale");
                p.put("addServiceBtn", "Tilføj Service");
                p.put("tabCalendar", "Månedskalender & Dagsprogram");
                p.put("tabClients", "Klientkartotek ({count})");
                p.put("tabServices", "Servicekatalog ({count})");
                p.put("appointmentsCountBadge", "{count} aftaler");
                p.put("singleAppointmentBadge", "1 aftale");
                p.put("activeWorkspacesTitle", "Aktive Workspaces ({count} åbne vinduer)");
                p.put("closeAllWindowsBtn", "Luk alle vinduer");
            }
            case "fr" -> {
                p.put("brandTitle", "Clinique EduJournal");
                p.put("therapistPortalSub", "Portail Thérapeute & Clinique");
                p.put("adminPortalSub", "Administration de la Plateforme (i18n)");
                p.put("clientPortalSub", "Portail Client Personnel");
                p.put("navClientOverview", "Mon Aperçu, Journal & Réservation");
                p.put("navClientSettings", "Mes Informations");
                p.put("navTherapistCalendar", "Calendrier Clinique & Espaces Clients");
                p.put("navTherapistSettings", "Clinique CVR & Horaires");
                p.put("dbLiveBadge", "PostgreSQL & JGit en direct");
                p.put("activeWorkspace", "Espace Actif");
                p.put("therapistClinicProfile", "Profil Clinique Thérapeute");
                p.put("personalClientProfile", "Profil Client Personnel");
                p.put("switchWorkspaceHeader", "Changer d'Espace / Profil");
                p.put("switchToTherapist", "Passer au Profil Thérapeute");
                p.put("switchToClient", "Passer au Profil Client");
                p.put("registerAsTherapistBtn", "S'inscrire comme Thérapeute (CVR)");
                p.put("adminI18nBtn", "Administration des Langues (i18n)");
                p.put("profileSettingsBtn", "Mon Profil & Paramètres");
                p.put("signOutBtn", "Se déconnecter");
                p.put("welcomeBack", "Bon retour, {name}");
                p.put("nextScheduledSession", "Prochaine Séance Prévue");
                p.put("noUpcomingAppointments", " aucun rendez-vous à venir");
                p.put("latestMoodScore", "Dernier Score d'Humeur");
                p.put("therapistConsentCard", "Consentement Thérapeute");
                p.put("activeTherapistsCount", "{count} Thérapeute(s) Actif(s)");
                p.put("pcl5DrawerBtn", "Auto-évaluation PCL-5");
                p.put("bookNewSessionBtn", "Réserver une Séance");
                p.put("tabJournal", "Mon Journal & PCL-5");
                p.put("tabAppointments", "Mes Rendez-vous ({count})");
                p.put("tabMarketplace", "Trouver un Thérapeute");
                p.put("tabConsent", "Accès Thérapeutes ({count})");
                p.put("therapistCalendarTitle", "Calendrier Clinique & Espaces Multi-Fenêtres");
                p.put("newAppointmentBtn", "Nouveau Rendez-vous");
                p.put("addServiceBtn", "Ajouter un Service");
                p.put("tabCalendar", "Calendrier Mensuel & Programme");
                p.put("tabClients", "Répertoire Clients ({count})");
                p.put("tabServices", "Catalogue des Services ({count})");
                p.put("appointmentsCountBadge", "{count} rdv");
                p.put("singleAppointmentBadge", "1 rdv");
                p.put("activeWorkspacesTitle", "Espaces Actifs ({count} fenêtres ouvertes)");
                p.put("closeAllWindowsBtn", "Fermer toutes les fenêtres");
            }
            case "es" -> {
                p.put("brandTitle", "Clínica EduJournal");
                p.put("therapistPortalSub", "Portal de Terapeuta y Clínica");
                p.put("adminPortalSub", "Administración de Plataforma (i18n)");
                p.put("clientPortalSub", "Portal Personal del Cliente");
                p.put("navClientOverview", "Mi Resumen, Diario y Reservas");
                p.put("navClientSettings", "Mis Datos Personales");
                p.put("navTherapistCalendar", "Calendario Clínico y Espacios");
                p.put("navTherapistSettings", "Clínica CVR y Horario");
                p.put("dbLiveBadge", "PostgreSQL y JGit Activo");
                p.put("activeWorkspace", "Espacio Activo");
                p.put("therapistClinicProfile", "Perfil Clínico de Terapeuta");
                p.put("personalClientProfile", "Perfil Personal del Cliente");
                p.put("switchWorkspaceHeader", "Cambiar Espacio / Perfil");
                p.put("switchToTherapist", "Cambiar a Perfil de Terapeuta");
                p.put("switchToClient", "Cambiar a Perfil de Cliente");
                p.put("registerAsTherapistBtn", "Registrarse como Terapeuta (CVR)");
                p.put("adminI18nBtn", "Gestión de Idiomas (i18n)");
                p.put("profileSettingsBtn", "Mi Perfil y Configuración");
                p.put("signOutBtn", "Cerrar sesión");
                p.put("welcomeBack", "Bienvenido de nuevo, {name}");
                p.put("nextScheduledSession", "Próxima Sesión Programada");
                p.put("noUpcomingAppointments", "Sin citas próximas");
                p.put("latestMoodScore", "Última Puntuación de Ánimo");
                p.put("therapistConsentCard", "Consentimiento Terapéutico");
                p.put("activeTherapistsCount", "{count} Terapeuta(s) Activo(s)");
                p.put("pcl5DrawerBtn", "Autoevaluación PCL-5");
                p.put("bookNewSessionBtn", "Reservar Nueva Sesión");
                p.put("tabJournal", "Mi Diario y PCL-5");
                p.put("tabAppointments", "Mis Citas ({count})");
                p.put("tabMarketplace", "Buscar Terapeuta y Reservar");
                p.put("tabConsent", "Acceso de Terapeutas ({count})");
                p.put("therapistCalendarTitle", "Calendario Clínico y Espacios Multi-Ventana");
                p.put("newAppointmentBtn", "Nueva Cita");
                p.put("addServiceBtn", "Añadir Servicio");
                p.put("tabCalendar", "Calendario Mensual y Agenda");
                p.put("tabClients", "Directorio de Clientes ({count})");
                p.put("tabServices", "Catálogo de Servicios ({count})");
                p.put("appointmentsCountBadge", "{count} citas");
                p.put("singleAppointmentBadge", "1 cita");
                p.put("activeWorkspacesTitle", "Espacios Activos ({count} ventanas abiertas)");
                p.put("closeAllWindowsBtn", "Cerrar todas las ventanas");
            }
            case "de" -> {
                p.put("brandTitle", "EduJournal Klinik");
                p.put("therapistPortalSub", "Therapeuten- & Klinikportal");
                p.put("adminPortalSub", "Plattform-Verwaltung (i18n)");
                p.put("clientPortalSub", "Persönliches Klientenportal");
                p.put("navClientOverview", "Übersicht, Tagebuch & Buchung");
                p.put("navClientSettings", "Meine Stammdaten");
                p.put("navTherapistCalendar", "Klinik-Kalender & Arbeitsbereiche");
                p.put("navTherapistSettings", "Klinik-CVR & Arbeitszeiten");
                p.put("dbLiveBadge", "PostgreSQL & JGit Aktiv");
                p.put("activeWorkspace", "Aktiver Arbeitsbereich");
                p.put("therapistClinicProfile", "Therapeuten-Klinikprofil");
                p.put("personalClientProfile", "Persönliches Klientenprofil");
                p.put("switchWorkspaceHeader", "Profil / Arbeitsbereich wechseln");
                p.put("switchToTherapist", "Zum Therapeutenprofil wechseln");
                p.put("switchToClient", "Zum Klientenprofil wechseln");
                p.put("registerAsTherapistBtn", "Als Therapeut registrieren (CVR)");
                p.put("adminI18nBtn", "Sprach- & i18n-Verwaltung");
                p.put("profileSettingsBtn", "Mein Profil & Einstellungen");
                p.put("signOutBtn", "Abmelden");
                p.put("welcomeBack", "Willkommen zurück, {name}");
                p.put("nextScheduledSession", "Nächste Geplante Sitzung");
                p.put("noUpcomingAppointments", "Keine anstehenden Termine");
                p.put("latestMoodScore", "Letzter Stimmungswert");
                p.put("therapistConsentCard", "Therapeuten-Freigabe");
                p.put("activeTherapistsCount", "{count} Aktive(r) Therapeut(en)");
                p.put("pcl5DrawerBtn", "PCL-5 Selbsteinschätzung");
                p.put("bookNewSessionBtn", "Neue Sitzung buchen");
                p.put("tabJournal", "Mein Tagebuch & PCL-5");
                p.put("tabAppointments", "Meine Termine ({count})");
                p.put("tabMarketplace", "Therapeuten finden & buchen");
                p.put("tabConsent", "Therapeuten-Zugriff ({count})");
                p.put("therapistCalendarTitle", "Klinik-Kalender & Multi-Fenster Arbeitsbereiche");
                p.put("newAppointmentBtn", "Neuer Termin");
                p.put("addServiceBtn", "Leistung hinzufügen");
                p.put("tabCalendar", "Monatskalender & Tagesplan");
                p.put("tabClients", "Klientenverzeichnis ({count})");
                p.put("tabServices", "Leistungskatalog ({count})");
                p.put("appointmentsCountBadge", "{count} Termine");
                p.put("singleAppointmentBadge", "1 Termin");
                p.put("activeWorkspacesTitle", "Aktive Arbeitsbereiche ({count} Fenster offen)");
                p.put("closeAllWindowsBtn", "Alle Fenster schließen");
            }
            default -> {
                p.put("brandTitle", "EduJournal Clinic");
                p.put("therapistPortalSub", "Therapist & Clinic Portal");
                p.put("adminPortalSub", "Platform Administration (i18n)");
                p.put("clientPortalSub", "Personal Client Portal");
                p.put("navClientOverview", "My Overview, Journal & Booking");
                p.put("navClientSettings", "My Personal Details");
                p.put("navTherapistCalendar", "Clinic Calendar & Client Workspaces");
                p.put("navTherapistSettings", "Clinic CVR & Working Hours");
                p.put("dbLiveBadge", "PostgreSQL & JGit Live");
                p.put("activeWorkspace", "Active Workspace");
                p.put("therapistClinicProfile", "Therapist Clinic Profile");
                p.put("personalClientProfile", "Personal Client Profile");
                p.put("switchWorkspaceHeader", "Switch Workspace / Profile");
                p.put("switchToTherapist", "Switch to Therapist Clinic Profile");
                p.put("switchToClient", "Switch to Personal Client Profile");
                p.put("registerAsTherapistBtn", "Register as Therapist (CVR)");
                p.put("adminI18nBtn", "Admin Language & i18n Manager");
                p.put("profileSettingsBtn", "My Profile & Settings");
                p.put("signOutBtn", "Sign out");
                p.put("welcomeBack", "Welcome back, {name}");
                p.put("nextScheduledSession", "Next Scheduled Session");
                p.put("noUpcomingAppointments", "No upcoming appointments");
                p.put("latestMoodScore", "Latest Mood Score");
                p.put("therapistConsentCard", "Therapist Consent");
                p.put("activeTherapistsCount", "{count} Active Therapist(s)");
                p.put("pcl5DrawerBtn", "PCL-5 Self-Assessment");
                p.put("bookNewSessionBtn", "Book New Session");
                p.put("tabJournal", "My Journal & PCL-5");
                p.put("tabAppointments", "My Appointments ({count})");
                p.put("tabMarketplace", "Find Therapist & Book");
                p.put("tabConsent", "Therapist Access ({count})");
                p.put("therapistCalendarTitle", "Clinic Calendar & Multi-Window Workspaces");
                p.put("newAppointmentBtn", "New Appointment");
                p.put("addServiceBtn", "Add Service");
                p.put("tabCalendar", "Monthly Calendar & Daily Schedule");
                p.put("tabClients", "Client Roster ({count})");
                p.put("tabServices", "Service Catalog ({count})");
                p.put("appointmentsCountBadge", "{count} appointments");
                p.put("singleAppointmentBadge", "1 appointment");
                p.put("activeWorkspacesTitle", "Active Workspaces ({count} open windows)");
                p.put("closeAllWindowsBtn", "Close all windows");
            }
        }
        return p;
    }
}
