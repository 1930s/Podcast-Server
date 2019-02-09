package lan.dk.podcastserver.repository;

import com.github.davinkevin.podcastserver.entity.Status;
import com.ninja_squad.dbsetup.Operations;
import com.ninja_squad.dbsetup.operation.CompositeOperation;
import com.ninja_squad.dbsetup.operation.Operation;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Optional;
import java.util.UUID;

import static com.ninja_squad.dbsetup.Operations.deleteAllFrom;
import static com.ninja_squad.dbsetup.Operations.insertInto;
import static com.ninja_squad.dbsetup.Operations.sequenceOf;
import static java.time.ZonedDateTime.now;

/**
 * Created by kevin on 17/08/15 for Podcast Server
 */
@Configuration
@EnableJpaRepositories(basePackages = "lan.dk.podcastserver.repository")
@EntityScan(basePackages = {"lan.dk.podcastserver.entity", "com.github.davinkevin.podcastserver.entity"})
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class DatabaseConfigurationTest {

    private static final Operation DELETE_ALL_PODCASTS = deleteAllFrom("PODCAST");
    private static final Operation DELETE_ALL_ITEMS = deleteAllFrom("ITEM");
    private static final Operation DELETE_ALL_TAGS = sequenceOf(deleteAllFrom("PODCAST_TAGS"), deleteAllFrom("TAG"));
    private static final Operation DELETE_ALL_PLAYLIST = Operations.sequenceOf(deleteAllFrom("WATCH_LIST_ITEMS"), deleteAllFrom("WATCH_LIST"));

    public static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(" ").append(DateTimeFormatter.ISO_LOCAL_TIME).toFormatter();
    public static final Operation DELETE_ALL = sequenceOf(DELETE_ALL_PLAYLIST, DELETE_ALL_ITEMS, DELETE_ALL_TAGS, DELETE_ALL_PODCASTS, DELETE_ALL_TAGS);

    @Bean DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(now());
    }

    public static final Operation INSERT_ITEM_DATA = CompositeOperation.sequenceOf(
            insertInto("PODCAST")
                    .columns("ID", "TITLE", "URL", "TYPE", "HAS_TO_BE_DELETED")
                    .values(UUID.fromString("e9c89e7f-7a8a-43ad-8425-ba2dbad2c561"), "AppLoad", null, "RSS", false)
                    .values(UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), "Geek Inc HD", "http://fake.url.com/rss", "YOUTUBE", true)
                    .build(),
            insertInto("ITEM")
                    .columns("ID", "TITLE", "URL", "PODCAST_ID", "STATUS", "PUB_DATE", "DOWNLOAD_DATE", "NUMBER_OF_FAIL")
                    .values(UUID.fromString("e3d41c71-37fb-4c23-a207-5fb362fa15bb"), "Appload 1", "http://fakeurl.com/appload.1.mp3", UUID.fromString("e9c89e7f-7a8a-43ad-8425-ba2dbad2c561"), Status.FINISH, now().minusDays(15).format(formatter), now().minusDays(15).format(formatter), 0)
                    .values(UUID.fromString("817a4626-6fd2-457e-8d27-69ea5acdc828"), "Appload 2", "http://fakeurl.com/appload.2.mp3", UUID.fromString("e9c89e7f-7a8a-43ad-8425-ba2dbad2c561"), null, now().minusDays(30).format(formatter), null, 0)
                    .values(UUID.fromString("43fb990f-0b5e-413f-920c-6de217f9ecdd"), "Appload 3", "http://fakeurl.com/appload.3.mp3", UUID.fromString("e9c89e7f-7a8a-43ad-8425-ba2dbad2c561"), Status.NOT_DOWNLOADED, now().format(formatter), null, 0)
                    .values(UUID.fromString("b721a6b6-896a-48fc-b820-28aeafddbb53"), "Geek INC 123", "http://fakeurl.com/geekinc.123.mp3", UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), Status.DELETED, now().minusYears(1).format(formatter), now().format(formatter), 0)
                    .values(UUID.fromString("0a774611-c857-44df-b7e0-5e5af31f7b56"), "Geek INC 124", "http://fakeurl.com/geekinc.124.mp3", UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), Status.FINISH, now().minusDays(15).format(formatter), now().minusDays(15).format(formatter), 0)
                    .values(UUID.fromString("0a774611-c867-44df-b7e0-5e5af31f7b56"), "Geek INC 122", "http://fakeurl.com/geekinc.122.mp3", UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), Status.FAILED, now().minusDays(1).format(formatter), null, 3)
                    .values(UUID.fromString("0a674611-c867-44df-b7e0-5e5af31f7b56"), "Geek INC 126", "http://fakeurl.com/geekinc.126.mp3", UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), Status.FAILED, now().minusDays(1).format(formatter), null, 7)
                    .build(),
            insertInto("TAG")
                    .columns("ID", "NAME")
                    .values(UUID.fromString("eb355a23-e030-4966-b75a-b70881a8bd08"), "French Spin")
                    .values(UUID.fromString("ad109389-9568-4bdb-ae61-5f26bf6ffdf6"), "Studio Knowhere")
                    .build(),
            insertInto("PODCAST_TAGS")
                    .columns("PODCASTS_ID", "TAGS_ID")
                    .values(UUID.fromString("e9c89e7f-7a8a-43ad-8425-ba2dbad2c561"), UUID.fromString("eb355a23-e030-4966-b75a-b70881a8bd08"))
                    .values(UUID.fromString("67b56578-454b-40a5-8d55-5fe1a14673e8"), UUID.fromString("ad109389-9568-4bdb-ae61-5f26bf6ffdf6"))
                    .build()
    );
}
