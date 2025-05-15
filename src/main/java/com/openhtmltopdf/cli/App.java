package com.openhtmltopdf.cli;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.util.XRLog;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class App
{
    @Command(
       name = "convert",
       description = "Converts a single html file into a PDF"
    )
    public static class Run implements Callable<Integer> {
        @Option(
            names = { "--input", "-i" },
            description = "The html input file",
            paramLabel = "<input>",
            required = true
        )
        File input;

        @Option(
            names = { "--font", "-f" },
            description = "Load truetype font",
            paramLabel = "<name>,<weight>,<filename>"
        )
        String[] fonts;

        @Option(
            names = { "--base" },
            description = "The base path (base uri) for resources",
            paramLabel = "<path>"
        )
        String basepath = null;

        @Option(
            names = { "--output", "-o" },
            description = "The PDF output file",
            paramLabel = "<output>",
            required = true
        )
        File output;

        @Option(
            names = { "--xhtml", "-x" },
            description = "Use to specify that the input file is valid XHTML (skipping the HTML to XHTML step)"
        )
        boolean xhtml = false;

        @Option(
            names = { "--verbose", "-v" },
            description = "Verbose logging"
        )
        boolean verbose = false;

        @Option(
            names = { "--quiet", "-q" },
            description = "Quiet logging"
        )
        boolean quiet = false;

        @Option(
            names = { "--block", "-b" },
            description = "Block linked resources (CSS, images, fonts)"
        )
        boolean block;

        @Option(
            names = { "--accessible", "-a"},
            description = "Force PDF/UA Conformance"
        )
        boolean accessible;

        @Override
        public Integer call() throws Exception {
            if (quiet && !verbose) {
                XRLog.listRegisteredLoggers().forEach(logger -> XRLog.setLevel(logger, Level.OFF));
            }
            if (!verbose && !quiet) {
                XRLog.listRegisteredLoggers().forEach(logger -> XRLog.setLevel(logger, Level.WARNING));
            }

            long timeStart = System.currentTimeMillis();
            if (!quiet) {
                System.out.println("Attempting to convert '" + input.getAbsolutePath() + "' to PDF at '" + output.getAbsolutePath() + "'");
            }

            try (FileOutputStream os = new FileOutputStream(output)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();

                builder.useSVGDrawer(new BatikSVGDrawer());

                if (block) {
                    builder.useUriResolver((base, rel) -> null);
                    builder.useExternalResourceAccessControl((uri, type) -> false, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
                    builder.useExternalResourceAccessControl((uri, type) -> false, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
                }

                if (fonts != null) {
                    for(String font : fonts) {
                        String[] split = font.split(",");
                        if (split.length != 3) {
                            System.err.println("Invalid font specification: " + font);
                            return 1;
                        }

                        String fontName = split[0];
                        int fontWeight = Integer.parseInt(split[1]);
                        String fontFile = split[2];
                        System.out.println("Loading font '"+fontFile+"' as '"+fontName+"' (weight "+fontWeight+")");
                        builder.useFont(new File(fontFile), fontName, fontWeight, FontStyle.NORMAL, false);
                    }
                }

                if (!xhtml) {
                    org.jsoup.nodes.Document jsoup = Jsoup.parse(input, "UTF-8");
                    Document doc = new W3CDom().fromJsoup(jsoup);

                    if (basepath != null) {
                        builder.withW3cDocument(doc, Paths.get(basepath).normalize().toUri().toURL().toExternalForm());
                    } else{
                        builder.withW3cDocument(doc, input.getAbsoluteFile().toURI().toURL().toExternalForm());
                    }
                } else {
                    builder.withFile(input);
                }

                if (accessible) {
                    builder.usePdfUaAccessbility(true);
                    builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_U);
                }

                builder.toStream(os);
                builder.useFastMode();
                builder.run();

                if (!quiet) {
                    System.out.println("Successfully created PDF in " + (System.currentTimeMillis() - timeStart) + "ms");
                }

                return 0;
            }
        }
    }


   @Command(
       mixinStandardHelpOptions = true,
       subcommands = { Run.class, CommandLine.HelpCommand.class },
       version = "openhtmltopdf-cli 1.0.10")
   public static class Cli {
   }

    public static void main( String[] args ) {
        int res = new CommandLine(new Cli()).execute(args);
        System.exit(res);
    }
}
