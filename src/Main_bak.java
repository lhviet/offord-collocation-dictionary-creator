import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Main_bak {

  static SQLiteConnection db = null;
  static SQLiteStatement statement;

  static int i = 0;
  static String[] words;
  static Charset selectedEncoding = StandardCharsets.UTF_8;

  static final String pageType_NOTE = "NOTE";
  static final String pageType_special_PDF = "PDF";

  static final String paragraph_openTag = "<P>";
  static final String paragraph_closeTag = "</P>";

  static int wordId = 0;
  static Map<String, Integer> wordTypeMap = new HashMap<>();
  static Map<String, Integer> usageMap = new HashMap<>();
  static Map<String, Integer> pageTypeMap = new HashMap<>();
  static Map<String, Integer> pageMap = new HashMap<>();
  static Map<String, Integer> wordHRefMap = new HashMap<>();

  public static void Main(String[] args) {

    String fileName = "offord_collocation.SQLite3";

    System.out.println("Bibooki Mobile Studio");
    System.out.println("Offord Collocation Dictionary Database Creator");

    try {
      db = new SQLiteConnection(new File(fileName));
      db.open(true);
      db.exec("pragma encoding=\"UTF-8\";");

      // prepare the database (create tables)
      prepareDatabase();

      // parsing the word htm files and insert data to the database
      insertWords();

      // parsing the NOTE htm files and insert data to the database
      insertNotes();

      /*for (String key: pageTypeMap.keySet()){
        System.out.println(key + " = "+ pageTypeMap.get(key));
      }
      for (String key: pageMap.keySet()){
        System.out.println(key + " = "+ pageMap.get(key));
      }*/
      /*for (String key: usageMap.keySet()){
        System.out.println(key + " = "+usageMap.get(key));
      }*/
      /*for (String key: wordTypeMap.keySet()){
        System.out.println(key + " = "+wordTypeMap.get(key));
      }*/
      /*for (String key: wordHRefMap.keySet()){
        System.out.println(" wordId "+key + " = "+wordHRefMap.get(key));
      }*/

      db.exec("VACUUM");
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
    }
  }

  /**
   * Prepare the database to store the words from Oxford Collocation Dictionary htm files
   * */
  static void prepareDatabase() {
    String query;
    if (db != null) {
      try {

        query = "CREATE TABLE IF NOT EXISTS word (wordId INTEGER PRIMARY KEY, word TEXT NOT NULL, type INTEGER NOT NULL) WITHOUT ROWID;";
        db.exec(query);

        query = "CREATE TABLE IF NOT EXISTS meaning (wordId INTEGER NOT NULL, meanOrder INTEGER NOT NULL, meaning TEXT NOT NULL, PRIMARY KEY(wordId,meanOrder)) WITHOUT ROWID;";
        db.exec(query);

        query = "CREATE TABLE IF NOT EXISTS usage (wordId INTEGER NOT NULL, meanOrder INTEGER, usageOrder INTEGER NOT NULL, usage TEXT, explanation TEXT NOT NULL, sentence TEXT NOT NULL);";
        db.exec(query);

        query = "CREATE TABLE IF NOT EXISTS ref_page (wordId INTEGER NOT NULL, meanOrder INTEGER, usageOrder INTEGER, pageType TEXT NOT NULL, page TEXT NOT NULL);";
        db.exec(query);

        clearTable("word");
        clearTable("meaning");
        clearTable("usage");
        clearTable("ref_page");

      } catch (Exception ex) {
        System.out.println("ERROR in prepareDatabase HAPPENED !!!");
        ex.printStackTrace();
      } finally {
      }
    }
  }

  /**
   * Retrieving all htm files in the given folder
   * Parsing
   * Storing data into the database
   */
  static void insertWords(){
    String[] words = new String[2];

    try{
      // Storing words based on the htm files
      db.exec("BEGIN TRANSACTION;");
      statement = db.prepare("INSERT INTO word (wordId,word,type) VALUES (?,?,?);");

      wordId = 0;
      Files.walk(Paths.get("./assets/OXFORD_PHRASEBUILDER_HTML/FILES")).forEach(filePath -> {
        if (Files.isRegularFile(filePath)) {

          try {

            wordId++;
            words[0] = filePath.getFileName().toString().substring(0, filePath.getFileName().toString().indexOf("_"));
            words[1] = filePath.getFileName().toString().substring(filePath.getFileName().toString().indexOf("_")+1,filePath.getFileName().toString().indexOf(".htm"));

            // killing some special cases
            if (words[1].endsWith("_noun"))
              words[1] = "noun";
            if (words[1].endsWith("_verb"))
              words[1] = "verb";

            statement.bind(1,wordId);
            statement.bind(2,words[0]);
            statement.bind(3,getWordTypeCode(words[1]));
            statement.step();
            statement.reset();

            //countWordType(words[1]);
          } catch (Exception e) {
            System.out.println("filePath = "+filePath);
            e.printStackTrace();
          } finally {
            //statement.dispose();
          }
        }
      });
      db.exec("COMMIT;"); //commit the transaction

      // Retrieving each file again, reading the content to parse & store
      wordId = 0;
      Files.walk(Paths.get("./assets/OXFORD_PHRASEBUILDER_HTML/FILES")).forEach(filePath -> {
        if (Files.isRegularFile(filePath)) {

          wordId++;
          words[0] = filePath.getFileName().toString().substring(0, filePath.getFileName().toString().indexOf("_"));
          words[1] = filePath.getFileName().toString().substring(filePath.getFileName().toString().indexOf("_")+1,filePath.getFileName().toString().indexOf(".htm"));

          // killing some special cases
          if (words[1].endsWith("_noun"))
            words[1] = "noun";
          if (words[1].endsWith("_verb"))
            words[1] = "verb";

          // parsing the page of word and insert its data into the database
          parsingData(wordId, filePath, FILE_TYPE.DEFAULT);
        }
      });
    }catch (Exception ex){
      ex.printStackTrace();
    }
  }

  static void parsingData(int wordId, Path filePath, FILE_TYPE fileType){
    try{

      String fileContent = readFile(filePath,selectedEncoding);
      fileContent = stripBody(fileContent);

      //countHRef(String.valueOf(wordId), fileContent);

      switch (fileType){
        case DEFAULT:
          parseInsertWordMeaning(wordId,fileContent);
          break;
        case NOTE:
          parseInsertNote(wordId,fileContent);
          break;
        default:
          break;
      }
    }catch (Exception ex){
      ex.printStackTrace();
    }finally {

    }
  }

  static void countWordType(String type){
    if (!wordTypeMap.containsKey(type)){
      wordTypeMap.put(type, 1);
    }else{
      wordTypeMap.put(type,wordTypeMap.get(type)+1);
    }
  }
  static void countUsageType(String type){
    if (!usageMap.containsKey(type)){
      usageMap.put(type, 1);
    }else{
      usageMap.put(type,usageMap.get(type)+1);
    }
  }
  static void countPageType(String type){
    if (!pageTypeMap.containsKey(type)){
      pageTypeMap.put(type, 1);
    }else{
      pageTypeMap.put(type, pageTypeMap.get(type)+1);
    }
  }
  static void countPage(String page){
    if (!pageMap.containsKey(page)){
      pageMap.put(page, 1);
    }else{
      pageMap.put(page, pageMap.get(page)+1);
    }
  }

  static void countHRef(String key, String htmlContentStr){
    String hrefSign = "<a href";
    String pdfSign = "Special page at ";
    if (htmlContentStr.toLowerCase().indexOf(hrefSign)>-1 || htmlContentStr.toLowerCase().indexOf(hrefSign)>-1 ){
      if (wordHRefMap.containsKey(key)){
        int count = wordHRefMap.get(key) + 1;
        wordHRefMap.put(key, count);
      }else{
        wordHRefMap.put(key,1);
      }
    }
  }

  /**
   * Read the content based on given file (path)
   * and return the string of content
   * @param path
   * @param encoding
   * @return
   * @throws IOException
   */
  static String readFile(Path path, Charset encoding)
      throws IOException
  {
    byte[] encoded = Files.readAllBytes(path);
    return new String(encoded, encoding);
  }

  static String stripBody(String htmlContentStr){
    htmlContentStr = htmlContentStr.substring(htmlContentStr.indexOf("</DIV>")+6);
    htmlContentStr = htmlContentStr.substring(0,htmlContentStr.indexOf("</BODY>"));
    return htmlContentStr.trim();
  }
  static void parseInsertWordMeaning(int wordId, String htmlContentStr){
    String meaningSUP_closeTag = "</SUP>";
    String meaningSUP_openTag = "<SUP>";
    String mean_openTag = "<TT>";
    String mean_closeTag = "</TT>";

    String mean = "", meanText;
    String[] means;
    int meanId = 0;

    try{
      String theFirstLine = htmlContentStr.substring(htmlContentStr.indexOf(paragraph_openTag),htmlContentStr.indexOf(paragraph_closeTag));

      // remove the first line of data ( word type )
      htmlContentStr = htmlContentStr.substring(htmlContentStr.indexOf(paragraph_closeTag)+paragraph_closeTag.length());

      if (htmlContentStr.indexOf(meaningSUP_closeTag) > 0){  // if there is more than one meaning (& usages)
        means = htmlContentStr.split(meaningSUP_closeTag);
        for (meanId = 1; meanId < means.length; meanId++) {

          mean = means[meanId].trim();
          if (mean.indexOf(meaningSUP_openTag) > 0){
            mean = mean.substring(0,mean.lastIndexOf(paragraph_openTag)).trim();
          }

          meanText = mean.substring(mean.indexOf(mean_openTag)+mean_openTag.length(), mean.indexOf(mean_closeTag)).trim();
          if (meanText.indexOf("&gt;") > -1){
            String pageType, page;
            if (meanText.indexOf("<A ")>0 && meanText.indexOf("</A>")>0){
              if (meanText.indexOf("NOTE_")>-1){
                page = meanText.substring(meanText.indexOf("<A "),meanText.indexOf("</A>"));
                page = page.substring(page.indexOf(">")+1).trim();
                if ((page.indexOf("PER") > -1 && page.trim().length() == 3)  || page.indexOf("PER CENT") > -1)
                  page = "PER_CENT";
                pageType = pageType_NOTE;
              }else{
                page = meanText.substring(meanText.indexOf("\""),meanText.indexOf(".htm"));
                pageType = page.substring(page.lastIndexOf("_")+1);
                page = page.substring(1,page.indexOf("_"));
                page = page.replaceAll("%20"," ").trim();
              }

              meanText = meanText.substring(0,meanText.indexOf("&gt;"));
              //countHRef("meaning_href_NOTE",mean);
            }else if (meanText.indexOf("&gt; Note at ")>0){
              String noteMark = "&gt; Note at ";
              page = meanText.substring(meanText.indexOf(noteMark)+noteMark.length());
              if (page.indexOf("</") > -1)
                page = page.substring(0,page.indexOf("</"));
              pageType = pageType_NOTE;
            }else{
              String pdfPage = "Special page at";
              page = meanText.substring(meanText.indexOf(pdfPage)+pdfPage.length());
              if (page.indexOf("</")>-1)
                page = page.substring(0,page.indexOf("</"));
              page = page.trim();
              pageType = pageType_special_PDF;

              meanText = meanText.substring(0,meanText.indexOf("&gt;"));
              //countHRef("meaning_href_PDF",mean);
            }
            insertRefOfWord(wordId, meanId, 0, pageType, page,false);
          }
          insertMeanOfWord(wordId,meanId,meanText.trim());

          if (mean.indexOf(paragraph_openTag)>0){
            mean = mean.substring(mean.indexOf(paragraph_openTag)).trim();
            parseInsertWordUsage(wordId, meanId, mean);
          }
        }
      }else{  // if there is only one meaning (& usages) of this word
        parseInsertWordUsage(wordId, meanId, htmlContentStr);
        meanId++;
      }
      if (theFirstLine.indexOf("&gt;")>-1){
        String pageType, page;
        //System.out.println("theFirstLine = "+theFirstLine);
        if (theFirstLine.indexOf("<A ")>0 && theFirstLine.indexOf("</A>")>0){
          if (theFirstLine.indexOf("NOTE_")>-1){
            page = theFirstLine.substring(theFirstLine.indexOf("<A "),theFirstLine.indexOf("</A>"));
            page = page.substring(page.indexOf(">")+1).trim();
            if ((page.indexOf("PER") > -1 && page.trim().length() == 3)  || page.indexOf("PER CENT") > -1)
              page = "PER_CENT";
            pageType = pageType_NOTE;
          }else{
            page = theFirstLine.substring(theFirstLine.indexOf("\""),theFirstLine.indexOf(".htm"));
            pageType = page.substring(page.lastIndexOf("_")+1);
            page = page.substring(1,page.indexOf("_"));
            page = page.replaceAll("%20"," ").trim();
          }
          //countHRef("first_line_note",mean);
        }else if (theFirstLine.indexOf("&gt; Note at ")>0){
          String noteMark = "&gt; Note at ";
          page = theFirstLine.substring(theFirstLine.indexOf(noteMark)+noteMark.length());
          if (page.indexOf("</") > -1)
            page = page.substring(0,page.indexOf("</"));
          pageType = pageType_NOTE;
        }else{
          String pdfPage = "Special page at";
          page = theFirstLine.substring(theFirstLine.indexOf(pdfPage)+pdfPage.length());
          page = page.substring(0,page.indexOf("</")).trim();
          pageType = pageType_special_PDF;

          //countHRef("first_line_pdf",mean);
        }
        insertRefOfWord(wordId, 0, 0, pageType, page,false);
      }
    }catch (Exception ex){
      /*System.out.println("htmlContentStr = "+htmlContentStr);
      System.out.println("mean = "+mean);*/
      ex.printStackTrace();
    }
  }

  /**
   * Insert meaning (usage explanation) of word into the meaning table
   * @param wordId
   * @param meanIndex
   * @param meanText
   */
  static void insertMeanOfWord(int wordId, int meanIndex, String meanText) {
    try{
      // Storing words based on the htm files
      db.exec("BEGIN TRANSACTION;");
      statement = db.prepare("INSERT INTO meaning (wordId,meanOrder,meaning) VALUES (?,?,?);");
      statement.bind(1,wordId);
      statement.bind(2,meanIndex);
      statement.bind(3,meanText);
      statement.step();
      statement.reset();
      db.exec("COMMIT;"); //commit the transaction
    }catch (Exception ex){
      ex.printStackTrace();
    } finally {
      //statement.dispose();
    }
  }

  static void insertRefOfWord(int wordId, int meanOrder, int usageOrder, String pageType, String page, boolean isSkip) {
    try{
      // Storing words based on the htm files
      if (isSkip==false)
        db.exec("BEGIN TRANSACTION;");
      SQLiteStatement st = db.prepare("INSERT INTO ref_page (wordId, meanOrder, usageOrder, pageType, page) VALUES (?,?,?,?,?);");
      st.bind(1,wordId);
      st.bind(2,meanOrder);
      st.bind(3,usageOrder);
      st.bind(4,getWordTypeCode(pageType));
      st.bind(5,page);
      /*countPageType(pageType);
      countPage(page);*/
      st.step();
      st.reset();
      if (isSkip==false)
        db.exec("COMMIT;"); //commit the transaction
    }catch (Exception ex){
      ex.printStackTrace();
    } finally {
      //statement.dispose();
    }
  }

  /**
   * Insert usage explanation of word into the usage table
   * @param wordId
   * @param meanIndex
   * @param htmlContentStr
   */
  static void parseInsertWordUsage(int wordId, int meanIndex, String htmlContentStr){
    String usage_openTag = "<U>";
    String usage_closeTag = "</U>";
    String explanation_openTag = "<B>";
    String explanation_closeTag = "</B>";
    String sentence_openTag = "<I>";
    String sentence_closeTag = "</I>";

    String usage,
        usageText,
        explanation,
        explanationText,
        sentence;
    String[] explanations;

    try {

      String[] usages = htmlContentStr.split(usage_openTag);

      db.exec("BEGIN TRANSACTION;");
      statement = db.prepare("INSERT INTO usage (wordId,meanOrder,usageOrder,usage,explanation,sentence) VALUES (?,?,?,?,?,?);");
      for (int usageIndex = 1; usageIndex < usages.length; usageIndex++) {
        usage = usages[usageIndex].trim();
        if (usage.endsWith("</P>\r\n<P>"))
          usage = usage.substring(0,usage.lastIndexOf("</P>\r\n<P>"));

        usageText = usage.substring(0, usage.indexOf(usage_closeTag)).trim();

        usage = usage.substring(usage.indexOf(explanation_openTag)+explanation_openTag.length()).trim();

        explanations = usage.split(Pattern.quote("|"));

        for (int explanationIndex = 0; explanationIndex < explanations.length; explanationIndex++) {

          explanation = explanations[explanationIndex].trim();

          // remove all explanation open & close Tags if existing in the current explanation string
          if (explanation.indexOf(explanation_openTag) > -1){
            explanation = explanation.replaceAll(explanation_openTag,"").trim();
          }
          if (explanation.indexOf(explanation_closeTag) > -1){
            explanation = explanation.replaceAll(explanation_closeTag,"").trim();
          }
          if (explanation.endsWith(paragraph_closeTag))
            explanation = explanation.substring(0,explanation.lastIndexOf(paragraph_closeTag)).trim();

          // checking and extract the reference of this usage of word
          if (explanation.indexOf("&gt;") > -1){
            String pageType, page;
            if (explanation.indexOf("<A ")>0 && explanation.indexOf("</A>")>0){
              if (explanation.indexOf("saucepan (for other collocates of_noun.htm") > -1){
                page = "saucepan";
                pageType = "noun";
              }else if (explanation.indexOf("NOTE_")>-1){
                page = explanation.substring(explanation.indexOf("<A "),explanation.indexOf("</A>"));
                page = page.substring(page.indexOf(">")+1).trim();
                if ((page.indexOf("PER") > -1 && page.trim().length() == 3)  || page.indexOf("PER CENT") > -1)
                  page = "PER_CENT";
                pageType = pageType_NOTE;
              }else{
                page = explanation.substring(explanation.indexOf("\""),explanation.indexOf(".htm"));
                pageType = page.substring(page.lastIndexOf("_")+1);
                page = page.substring(1,page.indexOf("_"));
                page = page.replaceAll("%20"," ").trim();
              }
              //countHRef("usage_href_NOTE",explanations[explanationIndex]);
            }else if (explanation.indexOf("&gt; Note at ")>0){
              String noteMark = "&gt; Note at ";
              page = explanation.substring(explanation.indexOf(noteMark)+noteMark.length());
              if (page.indexOf("</") > -1)
                page = page.substring(0,page.indexOf("</"));
              pageType = pageType_NOTE;
            }else{
              String pdfPage = "Special page at";
              page = explanation.substring(explanation.indexOf(pdfPage)+pdfPage.length()).trim();
              if (page.indexOf(" ")>-1)
                page = page.substring(0,page.indexOf(" "));
              else if (page.indexOf("<")>-1)
                page = page.substring(0,page.indexOf("<"));
              page = page.trim();
              pageType = pageType_special_PDF;

              //countHRef("usage_href_PDF",explanations[explanationIndex]);
            }
            explanation = explanation.substring(0,explanation.indexOf("&gt;"));
            insertRefOfWord(wordId, meanIndex, usageIndex, pageType, page,true);
          }

          // extract the sentence inside this block of usage explanation
          sentence = "";
          if (explanation.indexOf(sentence_openTag)>-1) {
            // there is associated sentence with this usage explanation, insert it
            if (explanation.indexOf(sentence_closeTag) > 0){
              sentence = explanation.substring(
                  explanation.indexOf(sentence_openTag) + sentence_openTag.length(),
                  explanation.indexOf(sentence_closeTag)
              ).trim();
            }else{
              sentence = explanation.substring(explanation.indexOf(sentence_openTag) + sentence_openTag.length()).trim();
            }
            explanation = explanation.substring(0,explanation.indexOf(sentence_openTag));
          }

          statement.bind(1, wordId);
          statement.bind(2, meanIndex);
          statement.bind(3, usageIndex);
          statement.bind(4, usageText);
          statement.bind(5, explanation.trim());
          statement.bind(6, sentence);
          statement.step();
          statement.reset();

          //countUsageType(usageText);
        }
      }
      db.exec("COMMIT;");
    } catch (StringIndexOutOfBoundsException e) {
      e.printStackTrace();
    } catch (SQLiteException e) {
      /*System.out.println("wordId = "+wordId);
      System.out.println("meanIndex = "+meanIndex);
      System.out.println("htmlContentStr = "+htmlContentStr);*/
      e.printStackTrace();
    } finally {
      //statement.dispose();
    }
  }

  /**
   * Retrieving all htm files in the given folder
   * Parsing
   * Storing data into the database
   */
  static void insertNotes(){

    try{
      // Storing words based on the htm files
      Files.walk(Paths.get("./assets/OXFORD_PHRASEBUILDER_HTML/NOTES")).forEach(filePath -> {
        if (Files.isRegularFile(filePath)) {
          try {
            wordId++;
            String type = "NOTE";
            String word = filePath.getFileName().toString().substring(filePath.getFileName().toString().indexOf("_")+1,filePath.getFileName().toString().indexOf(".htm"));

            db.exec("BEGIN TRANSACTION;");
            statement = db.prepare("INSERT INTO word (wordId,word,type) VALUES (?,?,?);");
            statement.bind(1,wordId);
            statement.bind(2,word);
            statement.bind(3, getWordTypeCode(type));
            statement.step();
            statement.reset();
            db.exec("COMMIT;"); //commit the transaction

            // parsing the page of word and insert its data into the database
            parsingData(wordId, filePath, FILE_TYPE.NOTE);

          } catch (Exception e) {
            System.out.println("filePath = "+filePath);
            e.printStackTrace();
          } finally {
            //statement.dispose();
          }
        }
      });

    }catch (Exception ex){
      ex.printStackTrace();
    }
  }
  static void parseInsertNote(int wordId, String htmlContentStr){

    String noteSign = "NOTE: ";
    String explanation_openTag = "<B>";
    String explanation_closeTag = "</B>";
    String sentence_openTag = "<I>";
    String sentence_closeTag = "</I>";

    String mean,
        explanation,
        sentence,
        usage = null;
    int meanOrder = 0;

    try{

      mean = htmlContentStr.substring(htmlContentStr.indexOf(noteSign)+noteSign.length(),htmlContentStr.indexOf(explanation_closeTag)).trim();

      insertMeanOfWord(wordId,meanOrder,mean);

      db.exec("BEGIN TRANSACTION;");
      statement = db.prepare("INSERT INTO usage (wordId,meanOrder,usageOrder,usage,explanation,sentence) VALUES (?,?,?,?,?,?);");

      // extract and insert explanations & sentences
      htmlContentStr = htmlContentStr.substring(htmlContentStr.indexOf(paragraph_closeTag)+paragraph_closeTag.length()).trim();

      String[] explanations = htmlContentStr.split(paragraph_closeTag);

      for (int usageIndex = 0; usageIndex < explanations.length; usageIndex++) {
        explanation = explanations[usageIndex].trim();

        // extract the sentence inside this block of usage explanation
        sentence = "";
        if (explanation.indexOf(explanation_openTag)>-1){
          if (explanation.indexOf(sentence_openTag)>-1) {
            // there is associated sentence with this usage explanation, insert it
            if (explanation.indexOf(sentence_closeTag) > 0){
              sentence = explanation.substring(
                  explanation.lastIndexOf(sentence_openTag) + sentence_openTag.length(),
                  explanation.lastIndexOf(sentence_closeTag)
              ).trim();
            }else{
              sentence = explanation.substring(explanation.lastIndexOf(sentence_openTag) + sentence_openTag.length()).trim();
            }
            explanation = explanation.substring(explanation.indexOf(explanation_openTag)+explanation_openTag.length(),explanation.lastIndexOf(explanation_closeTag)).trim();
          }
          // process the special case of NOTE_PERFORMANCE
          if (usageIndex+1 < explanations.length){
            String tempStr = explanations[usageIndex+1];
            if (tempStr.indexOf(explanation_openTag)<0
                && tempStr.indexOf(sentence_openTag)>0){
              tempStr = tempStr.substring(tempStr.indexOf(paragraph_openTag)+paragraph_openTag.length());
              tempStr = tempStr.replaceAll(sentence_openTag,"")
                  .replaceAll(sentence_closeTag,"");
              sentence = sentence + tempStr;
              usageIndex++;
            }
          }
        }
        else if (explanation.indexOf("&gt;")>-1){
          //System.out.println("theFirstLine = "+theFirstLine);
          String page, pageType;
          if (explanation.indexOf("<A ")>0 && explanation.indexOf("</A>")>0){
            if (explanation.indexOf("NOTE_")>-1){
              page = explanation.substring(explanation.indexOf("<A "),explanation.indexOf("</A>"));
              page = page.substring(page.indexOf(">")+1).trim();
              if ((page.indexOf("PER") > -1 && page.trim().length() == 3)  || page.indexOf("PER CENT") > -1)
                page = "PER_CENT";
              pageType = pageType_NOTE;
            }else{
              page = explanation.substring(explanation.indexOf("\""),explanation.indexOf(".htm"));
              pageType = page.substring(page.lastIndexOf("_")+1);
              page = page.substring(1,page.indexOf("_"));
              page = page.replaceAll("%20"," ").trim();
            }
            //countHRef("NOTE_href_NOTE",mean);
          }else if (explanation.indexOf("&gt; Note at ")>0){
            String noteMark = "&gt; Note at ";
            page = explanation.substring(explanation.indexOf(noteMark)+noteMark.length());
            if (page.indexOf("</") > -1)
              page = page.substring(0,page.indexOf("</"));
            pageType = pageType_NOTE;
          }else{
            String pdfPage = "Special page at";
            page = explanation.substring(explanation.indexOf(pdfPage)+pdfPage.length());
            page = page.substring(0,page.indexOf("</")).trim();
            pageType = pageType_special_PDF;

            //countHRef("NOTE_href_PDF",mean);
          }
          insertRefOfWord(wordId, 0, 0, pageType, page,true);
        }else{
          continue;
        }
        statement.bind(1, wordId);
        statement.bind(2, meanOrder);
        statement.bind(3, usageIndex+1);
        statement.bind(4, usage);
        statement.bind(5, explanation);
        statement.bind(6, sentence);
        statement.step();
        statement.reset();

        //countUsageType(usage);
      }
      db.exec("COMMIT;");

    }catch (Exception ex){
      ex.printStackTrace();
    }
  }

  static void clearTable(String table){
    SQLiteStatement st = null;
    System.out.println("DELETE FROM " + table);
    try {
      st = db.prepare("DELETE FROM " + table);
      st.step();

      st.dispose();
    } catch (SQLiteException e) {
      e.printStackTrace();
    } finally {
      try{
        if (st!=null)
          st.dispose();
      }catch (Exception e){
        e.printStackTrace();
      }
    }
  };

  static int getWordTypeCode(String wordType){
    int code = 0;
    switch (wordType){
      case "adv":
        code = 1;
        break;
      case "pron":
        code = 2;
        break;
      case "adj":
        code = 3;
        break;
      case "verb":
        code = 4;
        break;
      case "adj_adv":
        code = 5;
        break;
      case "varnish":
        code = 6;
        break;
      case "noun":
        code = 7;
        break;
      case "exclamation":
        code = 8;
        break;
      case "NOTE":
        code = 9;
        break;
      case "PDF":
        code = 10;
        break;
      default:
        break;
    }
    return code;
  }
}
