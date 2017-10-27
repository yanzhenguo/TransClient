package edu.shu;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * 与主站对应的rpc服务
 */
public interface IService extends Remote {
    String queryName(String no) throws RemoteException;
    void queryMessage(String no) throws RemoteException;
}
