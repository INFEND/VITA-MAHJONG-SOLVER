package dev.infend.vitasolver;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only provider for the exact OTA file, used by Android's installer. */
public final class UpdateFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File verified(Uri uri)throws FileNotFoundException {
        if(!"/apk".equals(uri.getPath()))throw new FileNotFoundException("Invalid OTA path");
        File f=new File(new File(getContext().getFilesDir(),"ota"),"vita-solver.apk");
        if(!f.isFile())throw new FileNotFoundException("Missing verified APK");
        return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(verified(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File file=verified(uri);
            MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
            cursor.addRow(new Object[]{"VITA-SOLVER.apk",file.length()});return cursor;
        }catch(Exception ex){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
}
