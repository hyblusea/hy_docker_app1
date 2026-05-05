package com.tradingx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class Ta4jRagConfig {

    @Value("${tradingx.rag.vector-store-path:./data/ta4j-vector-store.json}")
    private String vectorStorePath;

    @Bean
    public VectorStore ta4jVectorStore(org.springframework.ai.embedding.EmbeddingModel embeddingModel) throws IOException {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File vectorStoreFile = new File(vectorStorePath);
        if (vectorStoreFile.exists()) {
            log.info("Loading Ta4j vector store from cache: {}", vectorStoreFile.getAbsolutePath());
            vectorStore.load(vectorStoreFile);
        } else {
            log.info("Building Ta4j vector store from knowledge base documents...");
            List<Document> documents = loadTa4jDocuments();
            log.info("Loaded {} documents from knowledge base", documents.size());
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .build();
            List<Document> splitDocuments = splitter.apply(documents);
            log.info("Split into {} chunks, generating embeddings...", splitDocuments.size());
            vectorStore.add(splitDocuments);
            vectorStoreFile.getParentFile().mkdirs();
            vectorStore.save(vectorStoreFile);
            log.info("Ta4j vector store saved to: {}", vectorStoreFile.getAbsolutePath());
        }

        return vectorStore;
    }

    private List<Document> loadTa4jDocuments() throws IOException {
        List<Document> documents = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] txtResources = resolver.getResources("classpath:ta4j-knowledge/*.txt");
        for (Resource resource : txtResources) {
            TextReader reader = new TextReader(resource);
            reader.getCustomMetadata().put("source", resource.getFilename());
            documents.addAll(reader.get());
        }

        String[] subDirs = {"strategies", "indicators", "rules"};
        for (String dir : subDirs) {
            Resource[] javaResources = resolver.getResources("classpath:ta4j-knowledge/" + dir + "/*.java");
            for (Resource resource : javaResources) {
                TextReader reader = new TextReader(resource);
                reader.getCustomMetadata().put("source", dir + "/" + resource.getFilename());
                reader.getCustomMetadata().put("type", dir.equals("strategies") ? "strategy-example" : "api-example");
                documents.addAll(reader.get());
            }
        }

        return documents;
    }
}
