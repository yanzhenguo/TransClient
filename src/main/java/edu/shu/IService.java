package edu.shu;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by xc on 2017/9/14.
 */
public interface IService extends Remote {
    String queryName(String no) throws RemoteException;
    void queryMessage(String no) throws RemoteException;
}
