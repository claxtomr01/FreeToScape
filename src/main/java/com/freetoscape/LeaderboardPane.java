package com.freetoscape;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JTextPane;
import javax.swing.text.*;
import java.util.regex.*;

/** Styled plain text: player names are never interpreted as HTML. */
final class LeaderboardPane extends JTextPane {
    LeaderboardPane() {
        setEditable(false);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        setMargin(new Insets(10, 8, 10, 8));
    }

    @Override public void setText(String text) {
        super.setText(text);
        if (text == null || !(getDocument() instanceof StyledDocument)) return;
        StyledDocument document = getStyledDocument();
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, Font.SANS_SERIF);
        StyleConstants.setFontSize(normal, 12);
        StyleConstants.setForeground(normal, new Color(230, 230, 230));
        StyleConstants.setBold(normal, false);
        document.setCharacterAttributes(0, document.getLength(), normal, true);

        SimpleAttributeSet strong = new SimpleAttributeSet(normal);
        StyleConstants.setBold(strong, true);
        StyleConstants.setFontSize(strong, 14);
        SimpleAttributeSet gap = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(gap, new Color(165, 165, 165));
        SimpleAttributeSet spacing = new SimpleAttributeSet();
        StyleConstants.setSpaceAbove(spacing, 7);
        StyleConstants.setSpaceBelow(spacing, 2);
        Matcher lines = Pattern.compile("(?m)^.+$").matcher(text);
        while (lines.find()) {
            String line = lines.group();
            boolean entry = line.matches("\\d+\\. .+ — .+");
            boolean result = line.matches("(?:Winner|Joint winners|Final score|Winning margin|Margin over next score):.*");
            if (entry || result) {
                document.setCharacterAttributes(lines.start(), lines.end() - lines.start(), strong, false);
                document.setParagraphAttributes(lines.start(), lines.end() - lines.start(), spacing, false);
            } else if (line.startsWith("   ")) {
                document.setCharacterAttributes(lines.start(), lines.end() - lines.start(), gap, false);
            }
        }
        setCaretPosition(0);
    }
}
