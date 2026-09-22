package dev.applingo;

import android.os.RemoteException;

public class LocaleService extends ILocaleService.Stub {
    public LocaleService() {}
    @Override public String getLocales(String pkg, int user) throws RemoteException {
        try { return CommandRunner.get(pkg, user, false); }
        catch (Exception e) { throw new RemoteException(e.toString()); }
    }
    @Override public String setLocales(String pkg, int user, String tags) throws RemoteException {
        try { return CommandRunner.set(pkg, user, tags, false); }
        catch (Exception e) { throw new RemoteException(e.toString()); }
    }
    @Override public void destroy() { System.exit(0); }
}
