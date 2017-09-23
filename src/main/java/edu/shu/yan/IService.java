package edu.shu.yan;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by xc on 2017/9/14.
 */
public interface IService extends Remote {
    public String queryName(String no) throws RemoteException;
    public void queryMessage(String no) throws RemoteException;
}
