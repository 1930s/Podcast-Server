package lan.dk.podcastserver.manager.downloader;


import com.github.davinkevin.podcastserver.service.MimeTypeService;
import com.github.davinkevin.podcastserver.service.properties.PodcastServerParameters;
import io.vavr.control.Try;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.entity.Status;
import lan.dk.podcastserver.manager.ItemDownloadManager;
import lan.dk.podcastserver.repository.ItemRepository;
import lan.dk.podcastserver.repository.PodcastRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.vavr.API.Option;
import static io.vavr.API.Try;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
public abstract class AbstractDownloader implements Runnable, Downloader {

    /* Change visibility after kotlin Migration */ public static final String WS_TOPIC_DOWNLOAD = "/topic/download";

    protected Item item;
    protected DownloadingItem downloadingItem;
    /* Change visibility after kotlin Migration */ public ItemDownloadManager itemDownloadManager;

    protected final ItemRepository itemRepository;
    protected final PodcastRepository podcastRepository;
    protected final PodcastServerParameters podcastServerParameters;
    protected final SimpMessagingTemplate template;
    protected final MimeTypeService mimeTypeService;

    String temporaryExtension;
    /* Change visibility after kotlin Migration */ public  Path target;
    private PathMatcher hasTempExtensionMatcher;
    /* Change visibility after kotlin Migration */ public AtomicBoolean stopDownloading = new AtomicBoolean(false);

    public AbstractDownloader(ItemRepository itemRepository, PodcastRepository podcastRepository, PodcastServerParameters podcastServerParameters, SimpMessagingTemplate template, MimeTypeService mimeTypeService) {
        this.itemRepository = itemRepository;
        this.podcastRepository = podcastRepository;
        this.podcastServerParameters = podcastServerParameters;
        this.template = template;
        this.mimeTypeService = mimeTypeService;
    }


    @Override
    public void setDownloadingItem(DownloadingItem downloadingItem) {
        this.downloadingItem = downloadingItem;
        this.item = downloadingItem.getItem();
    }

    @Override
    public void run() {
        log.debug("Run");
        startDownload();
    }

    @Override
    public void startDownload() {
        item.setStatus(Status.STARTED);
        stopDownloading.set(false);
        saveSyncWithPodcast();
        convertAndSaveBroadcast();
        Try(this::download)
            .onFailure(e -> log.error("Error during download", e))
            .onFailure(e -> this.failDownload());
    }

    @Override
    public void pauseDownload() {
        item.setStatus(Status.PAUSED);
        stopDownloading.set(true);
        saveSyncWithPodcast();
        convertAndSaveBroadcast();
    }

    @Override
    public void stopDownload() {
        item.setStatus(Status.STOPPED);
        stopDownloading.set(true);
        saveSyncWithPodcast();
        itemDownloadManager.removeACurrentDownload(item);
        if (nonNull(target)) Try.run(() -> Files.deleteIfExists(target));
        convertAndSaveBroadcast();
    }

    public void failDownload() {
        item.setStatus(Status.FAILED);
        stopDownloading.set(true);
        item.addATry();
        saveSyncWithPodcast();
        itemDownloadManager.removeACurrentDownload(item);
        if (nonNull(target)) Try.run(() -> Files.deleteIfExists(target));
        convertAndSaveBroadcast();
    }

    @Override
    @Transactional
    public void finishDownload() {
        itemDownloadManager.removeACurrentDownload(item);

        if (isNull(target)) {
            failDownload();
            return;
        }

        item.setStatus(Status.FINISH);

        Try.run(() -> {
            if (hasTempExtensionMatcher.matches(target.getFileName())) {
                Path targetWithoutExtension = target.resolveSibling(target.getFileName().toString().replace(temporaryExtension, ""));

                Files.deleteIfExists(targetWithoutExtension);
                Files.move(target, targetWithoutExtension);

                target = targetWithoutExtension;
            }
            item
                .setLength(Files.size(target))
                .setMimeType(mimeTypeService.probeContentType(target));
        });

        item.setFileName(FilenameUtils.getName(target.getFileName().toString()));
        item.setDownloadDate(ZonedDateTime.now());

        saveSyncWithPodcast();
        convertAndSaveBroadcast();
    }

    @Transactional
    public Path getTargetFile(Item item) {

        if (nonNull(target)) return target;

        Path finalFile = getDestinationFile(item);
        log.debug("Creation of file : {}", finalFile.toFile().getAbsolutePath());

        try {
            if (Files.notExists(finalFile.getParent())) Files.createDirectories(finalFile.getParent());

            if (!(Files.exists(finalFile) || Files.exists(finalFile.resolveSibling(finalFile.getFileName() + temporaryExtension)))) {
                return finalFile.resolveSibling(finalFile.getFileName() + temporaryExtension);
            }

            log.info("Doublon sur le fichier en lien avec {} - {}, {}", item.getPodcast().getTitle(), item.getId(), item.getTitle() );
            return generateTempFileNextTo(finalFile);
        } catch (UncheckedIOException | IOException e) {
            log.error("Error during creation of target file", e);
            stopDownload();
            return null;
        }
    }

    Path generateTempFileNextTo(Path finalFile) {
        String fileName = finalFile.getFileName().toString();
        return Try.of(() -> Files.createTempFile(finalFile.getParent(), FilenameUtils.getBaseName(fileName) + "-", "." + FilenameUtils.getExtension(fileName) + temporaryExtension))
                .getOrElseThrow(e -> new UncheckedIOException(IOException.class.cast(e)));
    }

    private Path getDestinationFile(Item item) {
        String fileName = Option(downloadingItem.getFilename()).getOrElse(() -> getFileName(item));
        return  podcastServerParameters.getRootfolder().resolve(item.getPodcast().getTitle()).resolve(fileName);
    }

    @Transactional
    protected void saveSyncWithPodcast() {
        Try.run(() -> {
            Podcast podcast = podcastRepository.findById(item.getPodcast().getId()).orElseThrow(() -> new Error("Item with ID "+ item.getPodcast().getId() +" not found"));
            item.setPodcast(podcast);
            itemRepository.save(item);
        })
            .onFailure(e -> log.error("Error during save and Sync of the item {}", item, e));
    }

    @Transactional
    public void convertAndSaveBroadcast() {
        template.convertAndSend(WS_TOPIC_DOWNLOAD, item);
    }

    public String getItemUrl(Item item) {
        return downloadingItem.url().getOrElse(item::getUrl);
    }

    @PostConstruct
    public void postConstruct() {
        temporaryExtension = podcastServerParameters.getDownloadExtension();
        hasTempExtensionMatcher = FileSystems.getDefault().getPathMatcher("glob:*" + temporaryExtension);
    }

    public void setItemDownloadManager(ItemDownloadManager itemDownloadManager) {
        this.itemDownloadManager = itemDownloadManager;
    }

    public Item getItem() {
        return this.item;
    }

    public DownloadingItem getDownloadingItem() {
        return this.downloadingItem;
    }
}
