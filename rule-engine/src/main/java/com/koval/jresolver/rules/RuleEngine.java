package com.koval.jresolver.rules;

import java.io.IOException;
import java.util.Collection;

import org.drools.core.event.DebugAgendaEventListener;
import org.drools.core.event.DebugRuleRuntimeEventListener;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.KnowledgeBaseFactory;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.koval.jresolver.connector.bean.JiraIssue;


public class RuleEngine implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuleEngine.class);
  private final KieSession kieSession;

  public RuleEngine() throws IOException {
    final KnowledgeBuilder knowledgeBuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
    addRulesToKnowledgeBuilder(knowledgeBuilder);
    checkForErrors(knowledgeBuilder);
    LOGGER.info("Create kie session");
    kieSession = createSession(knowledgeBuilder);
  }

  private void addRulesToKnowledgeBuilder(KnowledgeBuilder knowledgeBuilder) throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader().getClass().getClassLoader();
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
    Resource[] resources = resolver.getResources("classpath*:rules/*.drl");
    for (Resource resource: resources) {
      String filePath = resource.getFile().getAbsolutePath();
      LOGGER.info("Add file to rule engine builder: {}", filePath);
      knowledgeBuilder.add(ResourceFactory.newFileResource(filePath), ResourceType.DRL);
    }
  }

  private void checkForErrors(KnowledgeBuilder knowledgeBuilder) {
    if (knowledgeBuilder.hasErrors()) {
      LOGGER.error(knowledgeBuilder.getErrors().toString());
      throw new RuntimeException("Unable to compile .drl files");
    }
  }

  private KieSession createSession(KnowledgeBuilder knowledgeBuilder) {
    final Collection<KiePackage> packages = knowledgeBuilder.getKnowledgePackages();
    final InternalKnowledgeBase knowledgeBase = KnowledgeBaseFactory.newKnowledgeBase();
    knowledgeBase.addPackages(packages);
    return knowledgeBase.newKieSession();
  }

  public void setDebugMode() {
    kieSession.addEventListener(new DebugAgendaEventListener());
    kieSession.addEventListener(new DebugRuleRuntimeEventListener());
  }

  public RulesResult execute(JiraIssue actualIssue) {
    RulesResult results = new RulesResult();
    kieSession.setGlobal("results", results);
    // insert facts into the session
    kieSession.insert(actualIssue);
    kieSession.fireAllRules();
    return results;
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("Dispose kie session");
    kieSession.dispose();
  }
}
