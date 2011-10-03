/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone;

import static javax.lang.model.SourceVersion.RELEASE_6;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.errorprone.matchers.ErrorProducingMatcher;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Messages;
import com.sun.tools.javac.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Entry point for running error-prone as a JSR-269 annotation processor.
 * @author Alex Eagle (alexeagle@google.com)
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(RELEASE_6)
public class ErrorProneProcessor extends AbstractProcessor {

  private Context context;

  @Override
  public void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    context = ((JavacProcessingEnvironment)processingEnv).getContext();

  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      String bundlePath = "/com/google/errorprone/errors.properties";
      InputStream bundleResource = getClass().getResourceAsStream(bundlePath);
      if (bundleResource == null) {
        throw new IllegalStateException("Resource bundle not found at " + bundlePath);
      }
      Messages.instance(context).add(new PropertyResourceBundle(bundleResource));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (!roundEnv.processingOver()) {
      JavacElements elementUtils = ((JavacProcessingEnvironment)processingEnv).getElementUtils();
      for (Element element : roundEnv.getRootElements()) {
        Pair<JCTree, JCCompilationUnit> treeAndTopLevel =
            elementUtils.getTreeAndTopLevel(element, null, null);
        if (treeAndTopLevel == null) {
          processingEnv.getMessager().printMessage(WARNING, "No tree found for element " + element);
        } else {
          ErrorReporter errorReporter = new JSR269ErrorReporter(
              Log.instance(context),
              processingEnv.getMessager(),
              treeAndTopLevel.snd.getSourceFile());
          List<ErrorProducingMatcher.AstError> astErrors = new ASTVisitor()
              .visitCompilationUnit(treeAndTopLevel.snd, new VisitorState());
          for (ErrorProducingMatcher.AstError astError : astErrors) {
            errorReporter.emitError(astError);
          }
        }
      }
    }
    return true;
  }

}
