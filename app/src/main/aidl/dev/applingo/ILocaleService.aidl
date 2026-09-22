package dev.applingo;
interface ILocaleService {
    String getLocales(String packageName, int userId) = 0;
    String setLocales(String packageName, int userId, String tags) = 1;
    void destroy() = 16777114;
}
