package dev.applingo;

import android.os.LocaleList;
import java.util.Locale;

/** Formats the actual system locale preference list, in priority order. */
final class PhoneLanguages {
    static String describe(LocaleList locales,Locale displayLocale){
        if(locales.isEmpty())return "Unavailable";
        StringBuilder text=new StringBuilder();
        for(int i=0;i<locales.size();i++){
            if(i>0)text.append("; ");
            Locale locale=locales.get(i);
            text.append(locale.getDisplayName(displayLocale)).append(" · ").append(locale.toLanguageTag());
        }
        return text.toString();
    }
}
