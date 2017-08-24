package edu.shu.yan.trans;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpsTrans {
    private void download(String key,String urlStr,String savePath) throws IOException{
        URL url = new URL(urlStr+"?key="+key);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        //���ó�ʱ��Ϊ3��
        conn.setConnectTimeout(3*1000);
        //��ֹ���γ���ץȡ������403����
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

        //�õ�������
        InputStream inputStream = conn.getInputStream();
        //��ȡ�ֽ�����
        byte[] getData = readInputStream(inputStream);

        File file = new File(savePath);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(getData);
        if(fos!=null){
            fos.close();
        }
        if(inputStream!=null){
            inputStream.close();
        }
    }
    public void upload(String key, String path, String fileName, String urlStr){

        try {
            URL url=new URL(urlStr+"?key="+key);
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.addRequestProperty("FileName", fileName);
            connection.setRequestProperty("content-type", "text/html");
            BufferedOutputStream  out=new BufferedOutputStream(connection.getOutputStream());

            //��ȡ�ļ��ϴ���������
            File file=new File(path);
            FileInputStream fileInputStream=new FileInputStream(file);
            byte[]bytes=new byte[1024];
            int numReadByte=0;
            while((numReadByte=fileInputStream.read(bytes,0,1024))>0)
            {
                out.write(bytes, 0, numReadByte);
            }

            out.flush();
            fileInputStream.close();
            //��ȡURLConnection����Ӧ
            DataInputStream in=new DataInputStream(connection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * ���������л�ȡ�ֽ�����
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static  byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }
    public static void main(String[] args) {
        HttpsTrans trans=new HttpsTrans();
        try{
            //trans.download("1236","http://localhost:8080/getFile","C:\\Users\\10539\\Downloads\\567.exe");
            trans.upload("1236","C:\\Users\\10539\\Downloads\\Git-2.13.0-32-bit.exe","Git-2.13.0-32-bit.exe","http://localhost:8080/uploadFile");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}
