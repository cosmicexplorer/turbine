package semanticdb;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import scala.Function1;
import scala.Option;
import scala.meta.internal.semanticdb.*;
import scala.meta.internal.semanticdb.Scala.ScalaSymbolOps;
import scala.meta.internal.semanticdb.Scala.Descriptor;

public class Reader {

  public static Index load(String path) {
    HashMap<String, SymbolInformation> infos = new HashMap<>();
    HashMap<String, String> anchors = new HashMap<>();

    TextDocuments payload = read(path);

    payload.documents().foreach((Function1<TextDocument, Void>) document -> {
      Map<String, Range> ranges = new HashMap<>();

      document.occurrences().foreach((Function1<SymbolOccurrence, Void>) so -> {
        Option<Range> r = so.range();
        if (r.isDefined()) {
          ranges.put(so.symbol(), r.get());
        }
        return null;
      });
      document.symbols().foreach((Function1<SymbolInformation, Void>) info -> {
        ScalaSymbolOps symOps = new ScalaSymbolOps(info.symbol());
        if (symOps.isGlobal()) {
          Descriptor desc = symOps.desc();
          if (desc.isTypeParameter() &&
              (desc.value() == "_" || desc.value().startsWith("anon$"))) {
          } else {
            infos.put(info.symbol(), info);
            String anchor = document.uri();
            Range range = ranges.get(info.symbol());
            String anchor2 = (range == null) ? anchor : ":" + (range.startLine() + 1);
            anchors.put(info.symbol(), anchor2);
          }
        }
        return null;
      });
      return null;
    });

    return new Index(infos, anchors);
  }

  public static class Index {
    public Index(Map<String, SymbolInformation> infos, Map<String, String> anchors) {
      this.infos = infos;
      this.anchors = anchors;
    }

    public Map<String, SymbolInformation> infos;
    public Map<String, String> anchors;
  }

  public static TextDocuments read(String path) {
    TextDocuments tds = null;
    try (InputStream stream = Files.newInputStream(Paths.get(path))) {
      tds = TextDocuments.messageCompanion().parseFrom(stream);
    } catch (Exception e) {
      System.out.println(e);
    }
    return tds;
  }

}
