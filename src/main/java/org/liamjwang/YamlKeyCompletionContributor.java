package org.liamjwang;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons.Nodes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.liamjwang.YamlKeyScanner.ConfigEntry;


public class YamlKeyCompletionContributor extends CompletionContributor {

    private Map<Project, YamlKeyScanner> configManagerMap = new HashMap<>();

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (isAStringLiteral(parameters.getPosition())) {

            String queryString = getQueryString(parameters);
            Project project = parameters.getEditor().getProject();

            if (project == null) {
                return;
            }

            if (!configManagerMap.containsKey(project)) {
                configManagerMap.put(project, new YamlKeyScanner(project.getBasePath()));
            }

            YamlKeyScanner thisYamlConfig = configManagerMap.get(project);

            for (ConfigEntry path : thisYamlConfig.getYamlConfigKeys()) {
//                if (!queryString.startsWith(path.path.substring(0, 1))) {
//                    continue;
//                }
                // TODO: Figure out a better way to set relevancy
                LookupElementBuilder element = LookupElementBuilder.create(path.path).withIcon(Nodes.DataTables).withTypeText(path.item.toString()).bold();
                result.withPrefixMatcher(queryString).addElement(element);
            }
        }
    }

    private static String getQueryString(CompletionParameters parameters) {
        int caretPositionInString = parameters.getOffset() - parameters.getPosition().getTextOffset();
        String queryString = parameters.getPosition()
                                       .getText()
                                       .substring(0, caretPositionInString);

        if (queryString.startsWith("'") || queryString.startsWith("\"")) {
            queryString = queryString.substring(1);
        }

        return queryString;
    }

    private static boolean isAStringLiteral(PsiElement element) {
        String text = element.getText();
        return (text.startsWith("\"") && text.endsWith("\"")) ||
               (text.startsWith("'") && text.endsWith("'")) ||
               getPreviousSiblingText(element).equals("\"") && getNextSiblingText(element).equals("\"") ||
               getPreviousSiblingText(element).equals("\'") && getNextSiblingText(element).equals("\'");
    }

    private static String getPreviousSiblingText(PsiElement element) {
        if (element.getPrevSibling() == null) return "";

        return element.getPrevSibling().getText();
    }

    private static String getNextSiblingText(PsiElement element) {
        if (element.getNextSibling() == null) return "";

        return element.getNextSibling().getText();
    }
}

