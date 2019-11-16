package com.koval.jresolver.docprocessor.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koval.jresolver.common.api.util.TextUtil;
import com.koval.jresolver.docprocessor.bean.MediaType;
import com.koval.jresolver.docprocessor.configuration.DocumentationProcessorProperties;
import com.koval.jresolver.docprocessor.convert.FileConverter;
import com.koval.jresolver.docprocessor.convert.impl.WordToPdfFileConverter;
import com.koval.jresolver.docprocessor.split.PageSplitter;
import com.koval.jresolver.docprocessor.split.impl.PdfPageSplitter;


public class DocDataSetCreator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocDataSetCreator.class);
  private static final String KEY_PREFIX = "doc_";
  private static final String SPACE = " ";
  private static final String SEPARATOR = "|";

  private final DocTypeDetector docTypeDetector = new DocTypeDetector();
  private final PageSplitter pageSplitter = new PdfPageSplitter();
  private final DocumentationProcessorProperties properties;

  public DocDataSetCreator(DocumentationProcessorProperties properties) {
    this.properties = properties;
  }

  public void create() throws IOException {
    File docsFolder = new File(properties.getDocsFolder());
    File[] docFiles = docsFolder.listFiles();
    if (docFiles == null) {
      LOGGER.warn("There are no documentation files");
      return;
    }

    File dataSetFile = new File(properties.getWorkFolder(), properties.getDataSetFileName());
    FileUtils.forceMkdir(dataSetFile.getParentFile());
    LOGGER.info("Folder to store data set file created: {}", dataSetFile.getParentFile().getCanonicalPath());
    LOGGER.info("Start creating data set file: {}", dataSetFile.getName());

    int pageIndex = 0;
    int documentIndex = 0;

    File docMetadataFile = new File(properties.getWorkFolder(), "doc-metadata.txt");
    File docListFile = new File(properties.getWorkFolder(), "doc-list.txt");

    try (PrintWriter dataSetOutput = new PrintWriter(dataSetFile, StandardCharsets.UTF_8.name());
         PrintWriter metadataOutput = new PrintWriter(docMetadataFile, StandardCharsets.UTF_8.name());
         PrintWriter docListOutput = new PrintWriter(docListFile, StandardCharsets.UTF_8.name())) {

      for (final File docFile : docFiles) {
        if (docFile.isFile()) {
          try (InputStream inputFileStream = new BufferedInputStream(new FileInputStream(docFile))) {
            MediaType mediaType = docTypeDetector.detectType(docFile.getName());
            if (mediaType.equals(MediaType.PDF)) {
              Map<Integer, String> docPages = pageSplitter.getMapping(inputFileStream);

              for (Map.Entry<Integer, String> docPage : docPages.entrySet()) {
                String docPageKey = KEY_PREFIX + pageIndex;
                pageIndex++;

                dataSetOutput.print(docPageKey);
                dataSetOutput.print(SEPARATOR);
                dataSetOutput.println(TextUtil.simplify(docPage.getValue()));

                metadataOutput.print(docPageKey);
                metadataOutput.print(SPACE);
                metadataOutput.print(documentIndex);
                metadataOutput.print(SPACE);
                metadataOutput.println(docPage.getKey());
              }

              docListOutput.print(documentIndex);
              docListOutput.print(SPACE);
              docListOutput.println(docFile.getName());
              documentIndex++;
            }
          } catch (FileNotFoundException e) {
            LOGGER.error("Could not find documentation file: " + docFile.getAbsolutePath(), e);
          }
        }
      }
      LOGGER.info("Data set file was created: {}", dataSetFile.getCanonicalPath());
      LOGGER.info("Doc metadata file was created: {}", docMetadataFile.getCanonicalPath());
      LOGGER.info("Doc list file was created: {}", docListFile.getCanonicalPath());
    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      LOGGER.error("Could not write to output file", e);
    }
  }

  public void convertWordFilesToPdf() {
    File docsFolder = new File(properties.getDocsFolder());
    File[] docFiles = docsFolder.listFiles();
    if (docFiles == null) {
      LOGGER.warn("There are no documentation files");
      return;
    }
    FileConverter fileConverter = new WordToPdfFileConverter();
    for (final File docFile : docFiles) {
      if (docFile.isFile()) {
        MediaType mediaType = docTypeDetector.detectType(docFile.getName());
        if (mediaType.equals(MediaType.WORD)) {
          fileConverter.convert(docFile);
        }
      }
    }
  }
}
