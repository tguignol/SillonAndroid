package ch.kohlnet.sillon.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ch.kohlnet.sillon.data.LanguageManager

/**
 * Chaînes localisées de l'interface (chrome visible) — équivalent des `.strings` iOS résolus par
 * `LanguageManager.string(key)`. Base = français ; repli sur le français si une traduction manque.
 * Les 11 langues du sélecteur sont couvertes pour les libellés principaux ; les écrans de détail
 * migreront progressivement vers ces clés.
 */
enum class S {
    ACCUEIL, BIBLIOTHEQUE, FAVORIS, RECHERCHE, REGLAGES,
    APPARENCE, SYSTEME, CLAIR, SOMBRE, LANGUE,
    SERVEURS, RAFRAICHIR, PRIORITE_HINT, AUCUN_SERVEUR, AJOUTER_SERVEUR, ADRESSE_SERVEUR, UTILISATEUR, MOT_DE_PASSE, AJOUTER,
    MODIFIER_SERVEUR, NOM, ENREGISTRER, ANNULER, MDP_GARDER, CHOISIR_DOSSIER,
    EGALISEUR, ACTIVE, INACTIF, REINITIALISER, BANDES,
    CONNEXION_EN_COURS, AJOUTE,
    ALBUMS_RECENTS, ALBUMS_PREFERES, ALBUMS_ALEATOIRES, REDECOUVRIR,
    BIBLIOTHEQUE_VIDE, AUCUN_FAVORI, CHARGEMENT, ALBUMS, ARTISTES,
    OUT_BLUETOOTH, OUT_WIRED, OUT_SPEAKER,
}

/** Construit la table d'une clé : fr est obligatoire (base), les autres sont optionnelles. */
private fun tr(
    fr: String, en: String, de: String, it: String, es: String,
    pt: String, sq: String, sr: String, rm: String, tr: String,
) = mapOf(
    "fr" to fr, "en" to en, "de" to de, "it" to it, "es" to es,
    "pt" to pt, "sq" to sq, "sr" to sr, "rm" to rm, "tr" to tr,
)

private val TABLE: Map<S, Map<String, String>> = mapOf(
    S.ACCUEIL to tr("Accueil", "Home", "Startseite", "Home", "Inicio", "Início", "Kreu", "Početna", "Partenza", "Ana sayfa"),
    S.BIBLIOTHEQUE to tr("Bibliothèque", "Library", "Mediathek", "Libreria", "Biblioteca", "Biblioteca", "Biblioteka", "Biblioteka", "Biblioteca", "Kitaplık"),
    S.FAVORIS to tr("Favoris", "Favorites", "Favoriten", "Preferiti", "Favoritos", "Favoritos", "Të preferuarat", "Omiljeno", "Preferids", "Favoriler"),
    S.RECHERCHE to tr("Recherche", "Search", "Suche", "Cerca", "Buscar", "Pesquisar", "Kërko", "Pretraga", "Tschertgar", "Ara"),
    S.REGLAGES to tr("Réglages", "Settings", "Einstellungen", "Impostazioni", "Ajustes", "Definições", "Cilësimet", "Podešavanja", "Parameters", "Ayarlar"),
    S.APPARENCE to tr("Apparence", "Appearance", "Erscheinungsbild", "Aspetto", "Apariencia", "Aparência", "Pamja", "Izgled", "Apparientscha", "Görünüm"),
    S.SYSTEME to tr("Système", "System", "System", "Sistema", "Sistema", "Sistema", "Sistemi", "Sistem", "Sistem", "Sistem"),
    S.CLAIR to tr("Clair", "Light", "Hell", "Chiaro", "Claro", "Claro", "E çelët", "Svetlo", "Cler", "Açık"),
    S.SOMBRE to tr("Sombre", "Dark", "Dunkel", "Scuro", "Oscuro", "Escuro", "E errët", "Tamno", "Stgir", "Koyu"),
    S.LANGUE to tr("Langue", "Language", "Sprache", "Lingua", "Idioma", "Idioma", "Gjuha", "Jezik", "Lingua", "Dil"),
    S.SERVEURS to tr("Serveurs", "Servers", "Server", "Server", "Servidores", "Servidores", "Serverë", "Serveri", "Servers", "Sunucular"),
    S.RAFRAICHIR to tr("Rafraîchir", "Refresh", "Aktualisieren", "Aggiorna", "Actualizar", "Atualizar", "Rifresko", "Osveži", "Actualisar", "Yenile"),
    S.PRIORITE_HINT to tr(
        "Le serveur du haut prime sur les doublons — mets ton serveur FLAC en premier.",
        "The top server wins on duplicates — put your FLAC server first.",
        "Der oberste Server gewinnt bei Duplikaten — stell deinen FLAC-Server nach oben.",
        "Il server in alto vince sui duplicati — metti il server FLAC per primo.",
        "El servidor superior gana en duplicados — pon tu servidor FLAC primero.",
        "O servidor de topo vence nos duplicados — põe o servidor FLAC primeiro.",
        "Serveri i sipërm fiton te dublikatat — vendos serverin FLAC të parin.",
        "Gornji server pobeđuje kod duplikata — stavi FLAC server prvi.",
        "Il server da sura prevala tar ils dubels — metta tes server FLAC l'emprim.",
        "Üstteki sunucu yinelenenlerde kazanır — FLAC sunucunu en üste koy.",
    ),
    S.AUCUN_SERVEUR to tr("Aucun serveur.", "No server.", "Kein Server.", "Nessun server.", "Ningún servidor.", "Nenhum servidor.", "Asnjë server.", "Nema servera.", "Nagin server.", "Sunucu yok."),
    S.AJOUTER_SERVEUR to tr("Ajouter un serveur", "Add a server", "Server hinzufügen", "Aggiungi un server", "Añadir un servidor", "Adicionar um servidor", "Shto një server", "Dodaj server", "Agiuntar in server", "Sunucu ekle"),
    S.ADRESSE_SERVEUR to tr("Adresse du serveur", "Server address", "Serveradresse", "Indirizzo del server", "Dirección del servidor", "Endereço do servidor", "Adresa e serverit", "Adresa servera", "Adressa dal server", "Sunucu adresi"),
    S.UTILISATEUR to tr("Utilisateur", "Username", "Benutzername", "Nome utente", "Usuario", "Utilizador", "Përdoruesi", "Korisničko ime", "Num d'utilisader", "Kullanıcı adı"),
    S.MOT_DE_PASSE to tr("Mot de passe", "Password", "Passwort", "Password", "Contraseña", "Palavra-passe", "Fjalëkalimi", "Lozinka", "Pled-clav", "Parola"),
    S.AJOUTER to tr("Ajouter", "Add", "Hinzufügen", "Aggiungi", "Añadir", "Adicionar", "Shto", "Dodaj", "Agiuntar", "Ekle"),
    S.MODIFIER_SERVEUR to tr("Modifier le serveur", "Edit server", "Server bearbeiten", "Modifica server", "Editar servidor", "Editar servidor", "Modifiko serverin", "Izmeni server", "Modifitgar server", "Sunucuyu düzenle"),
    S.NOM to tr("Nom", "Name", "Name", "Nome", "Nombre", "Nome", "Emri", "Ime", "Num", "Ad"),
    S.ENREGISTRER to tr("Enregistrer", "Save", "Speichern", "Salva", "Guardar", "Guardar", "Ruaj", "Sačuvaj", "Memorisar", "Kaydet"),
    S.ANNULER to tr("Annuler", "Cancel", "Abbrechen", "Annulla", "Cancelar", "Cancelar", "Anulo", "Otkaži", "Annullar", "İptal"),
    S.CHOISIR_DOSSIER to tr("Choisir un dossier…", "Choose a folder…", "Ordner wählen…", "Scegli una cartella…", "Elegir una carpeta…", "Escolher uma pasta…", "Zgjidh një dosje…", "Izaberi fasciklu…", "Tscherner in ordinatur…", "Bir klasör seç…"),
    S.EGALISEUR to tr("Égaliseur", "Equalizer", "Equalizer", "Equalizzatore", "Ecualizador", "Equalizador", "Ekualizues", "Ekvilajzer", "Egalisader", "Ekolayzer"),
    S.ACTIVE to tr("Activé", "On", "Ein", "Attivo", "Activado", "Ativado", "Aktiv", "Uključeno", "Activà", "Açık"),
    S.INACTIF to tr("Désactivé", "Off", "Aus", "Disattivato", "Desactivado", "Desativado", "Joaktiv", "Isključeno", "Deactivà", "Kapalı"),
    S.REINITIALISER to tr("Réinitialiser", "Reset", "Zurücksetzen", "Reimposta", "Restablecer", "Repor", "Rivendos", "Resetuj", "Reinizialisar", "Sıfırla"),
    S.BANDES to tr("Bandes", "Bands", "Bänder", "Bande", "Bandas", "Bandas", "Brezat", "Opsezi", "Bandas", "Bantlar"),
    S.MDP_GARDER to tr(
        "Mot de passe (vide = inchangé)", "Password (blank = unchanged)", "Passwort (leer = unverändert)",
        "Password (vuoto = invariato)", "Contraseña (vacío = sin cambios)", "Palavra-passe (vazio = inalterada)",
        "Fjalëkalimi (bosh = pa ndryshim)", "Lozinka (prazno = nepromenjeno)", "Pled-clav (vid = nunamodifitgà)",
        "Parola (boş = değişmez)",
    ),
    S.CONNEXION_EN_COURS to tr("Connexion…", "Connecting…", "Verbinden…", "Connessione…", "Conectando…", "A ligar…", "Po lidhet…", "Povezivanje…", "Connexiun…", "Bağlanıyor…"),
    S.AJOUTE to tr("Ajouté", "Added", "Hinzugefügt", "Aggiunto", "Añadido", "Adicionado", "U shtua", "Dodato", "Agiuntà", "Eklendi"),
    S.ALBUMS_RECENTS to tr("Albums récents", "Recent albums", "Neueste Alben", "Album recenti", "Álbumes recientes", "Álbuns recentes", "Albumet e fundit", "Najnoviji albumi", "Albums novs", "Son albümler"),
    S.ALBUMS_PREFERES to tr("Albums préférés", "Favorite albums", "Lieblingsalben", "Album preferiti", "Álbumes favoritos", "Álbuns favoritos", "Albumet e preferuara", "Omiljeni albumi", "Albums preferids", "Favori albümler"),
    S.ALBUMS_ALEATOIRES to tr("Albums aléatoires", "Random albums", "Zufällige Alben", "Album casuali", "Álbumes aleatorios", "Álbuns aleatórios", "Albume të rastësishme", "Nasumični albumi", "Albums casuals", "Rastgele albümler"),
    S.REDECOUVRIR to tr("Redécouvrir des albums", "Rediscover albums", "Alben wiederentdecken", "Riscopri album", "Redescubrir álbumes", "Redescobrir álbuns", "Rizbulo albume", "Ponovo otkrij albume", "Redescovrir albums", "Albümleri yeniden keşfet"),
    S.BIBLIOTHEQUE_VIDE to tr(
        "Aucun album.\nConnecte-toi à un serveur dans Réglages.",
        "No albums.\nConnect to a server in Settings.",
        "Keine Alben.\nVerbinde dich in den Einstellungen mit einem Server.",
        "Nessun album.\nConnettiti a un server nelle Impostazioni.",
        "Ningún álbum.\nConéctate a un servidor en Ajustes.",
        "Nenhum álbum.\nLiga-te a um servidor nas Definições.",
        "Asnjë album.\nLidhu me një server te Cilësimet.",
        "Nema albuma.\nPoveži se sa serverom u Podešavanjima.",
        "Nagins albums.\nConnectescha tai cun in server en ils parameters.",
        "Albüm yok.\nAyarlar'dan bir sunucuya bağlan.",
    ),
    S.CHARGEMENT to tr("Chargement…", "Loading…", "Wird geladen…", "Caricamento…", "Cargando…", "A carregar…", "Po ngarkohet…", "Učitavanje…", "Chargiar…", "Yükleniyor…"),
    S.ALBUMS to tr("Albums", "Albums", "Alben", "Album", "Álbumes", "Álbuns", "Albume", "Albumi", "Albums", "Albümler"),
    S.ARTISTES to tr("Artistes", "Artists", "Künstler", "Artisti", "Artistas", "Artistas", "Artistët", "Izvođači", "Artists", "Sanatçılar"),
    S.OUT_BLUETOOTH to tr("Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth", "Bluetooth"),
    S.OUT_WIRED to tr("Casque", "Headphones", "Kopfhörer", "Cuffie", "Auriculares", "Auscultadores", "Kufje", "Slušalice", "Cufftgas", "Kulaklık"),
    S.OUT_SPEAKER to tr("Haut-parleur", "Speaker", "Lautsprecher", "Altoparlante", "Altavoz", "Altifalante", "Altoparlant", "Zvučnik", "Plicontrol", "Hoparlör"),
    S.AUCUN_FAVORI to tr(
        "Aucun favori.\nTouche le cœur sur un album.",
        "No favorites.\nTap the heart on an album.",
        "Keine Favoriten.\nTippe auf das Herz eines Albums.",
        "Nessun preferito.\nTocca il cuore su un album.",
        "Ningún favorito.\nToca el corazón en un álbum.",
        "Nenhum favorito.\nToca no coração de um álbum.",
        "Asnjë i preferuar.\nPrek zemrën te një album.",
        "Nema omiljenih.\nDodirni srce na albumu.",
        "Nagins preferids.\nTutga sin il cor d'in album.",
        "Favori yok.\nBir albümdeki kalbe dokun.",
    ),
)

/** Résolution non-composable (pour usages hors composition). */
fun localized(key: S, code: String): String =
    TABLE[key]?.let { it[code] ?: it["fr"] } ?: key.name

/** Chaîne localisée selon la langue courante ; se recompose au changement de langue. */
@Composable
fun str(key: S): String {
    val lang by LanguageManager.current.collectAsState()
    return localized(key, LanguageManager.resolvedCode(lang))
}
