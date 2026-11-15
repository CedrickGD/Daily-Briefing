import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Perceptual hashes for the reader's duplicate survey (ArticleDuplicateSurvey in the unit tests):
 * downloads every picture listed in images-&lt;label&gt;.txt once and appends "url TAB WxH hash,hash,…"
 * to hashes.tsv – a difference hash of the whole picture and of its centre crops (16:9, 3:2, 4:3,
 * 1:1, 2:1), so "the same picture" can be judged by pixels instead of by the URL rules under test.
 * Pictures Java cannot decode (WebP, AVIF) are noted with a leading "-". Not part of the app.
 *
 * <pre>java scripts/HashImages.java &lt;images.txt&gt; &lt;hashes.tsv&gt;</pre>
 */
public class HashImages {
    public static void main(String[] args) throws Exception {
        Path list = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Set<String> known = new HashSet<>();
        if (Files.exists(out)) for (String line : Files.readAllLines(out)) if (line.contains("\t")) known.add(line.substring(0, line.indexOf('\t')));
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> jobs = new ArrayList<>();
        for (String url : Files.readAllLines(list)) {
            if (url.isBlank() || known.contains(url)) continue;
            jobs.add(pool.submit(() -> url + "\t" + hash(http, url)));
        }
        int ok = 0;
        for (Future<String> job : jobs) {
            String line = job.get();
            if (!line.contains("\t-")) ok++;
            Files.writeString(out, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        pool.shutdown();
        System.out.println("hashed " + ok + " of " + jobs.size());
    }

    static String hash(HttpClient http, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Accept", "image/jpeg,image/png;q=0.9,image/gif;q=0.8,*/*;q=0.3")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) return "-http" + response.statusCode();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) return "-format " + response.headers().firstValue("content-type").orElse("?");
            StringBuilder sb = new StringBuilder(image.getWidth() + "x" + image.getHeight() + " ");
            sb.append(Long.toHexString(dhash(image, 0, 0, image.getWidth(), image.getHeight())));
            double current = (double) image.getWidth() / image.getHeight();
            for (double ratio : new double[]{16.0 / 9, 3.0 / 2, 4.0 / 3, 1.0, 2.0}) {
                if (Math.abs(current - ratio) < 0.04) continue;
                if (current > ratio) {
                    int w = (int) (image.getHeight() * ratio);
                    sb.append(',').append(Long.toHexString(dhash(image, (image.getWidth() - w) / 2, 0, w, image.getHeight())));
                } else {
                    int h = (int) (image.getWidth() / ratio);
                    sb.append(',').append(Long.toHexString(dhash(image, 0, (image.getHeight() - h) / 2, image.getWidth(), h)));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "-" + e.getClass().getSimpleName();
        }
    }

    static long dhash(BufferedImage image, int x, int y, int w, int h) {
        BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        var g = small.createGraphics();
        g.drawImage(image.getSubimage(x, y, Math.max(1, w), Math.max(1, h)).getScaledInstance(9, 8, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        long bits = 0;
        var raster = small.getRaster();
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            bits = (bits << 1) | (raster.getSample(col, row, 0) > raster.getSample(col + 1, row, 0) ? 1L : 0L);
        }
        return bits;
    }
}
