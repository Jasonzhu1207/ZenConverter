use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::PathBuf;

use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::jbyteArray;
use jni::JNIEnv;
use office2pdf::config::{ConvertOptions, Format};

#[no_mangle]
pub extern "system" fn Java_org_zenconverter_app_office_Office2PdfNative_convertBytes(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JByteArray<'_>,
    extension: JString<'_>,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        convert_bytes(&mut env, &input, &extension, Vec::new())
    }));
    finish_conversion(&mut env, result)
}

#[no_mangle]
pub extern "system" fn Java_org_zenconverter_app_office_Office2PdfNative_convertBytesWithFontPaths(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JByteArray<'_>,
    extension: JString<'_>,
    font_directories: JObjectArray<'_>,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let font_paths = read_font_directories(&mut env, &font_directories)?;
        convert_bytes(&mut env, &input, &extension, font_paths)
    }));
    finish_conversion(&mut env, result)
}

fn finish_conversion(
    env: &mut JNIEnv<'_>,
    result: std::thread::Result<Result<Vec<u8>, String>>,
) -> jbyteArray {
    match result {
        Ok(Ok(pdf)) => match env.byte_array_from_slice(&pdf) {
            Ok(array) => array.into_raw(),
            Err(error) => throw_failure(env, format!("Could not create PDF byte array: {error}")),
        },
        Ok(Err(message)) => throw_failure(env, message),
        Err(_) => throw_failure(env, "Office native conversion panicked".to_string()),
    }
}

fn convert_bytes(
    env: &mut JNIEnv<'_>,
    input: &JByteArray<'_>,
    extension: &JString<'_>,
    font_paths: Vec<PathBuf>,
) -> Result<Vec<u8>, String> {
    let input = env
        .convert_byte_array(input)
        .map_err(|error| format!("Could not read Office input bytes: {error}"))?;
    let extension: String = env
        .get_string(extension)
        .map_err(|error| format!("Could not read Office extension: {error}"))?
        .into();
    let format = Format::from_extension(extension.trim().trim_start_matches('.'))
        .ok_or_else(|| format!("Unsupported Office extension: {extension}"))?;
    let options = ConvertOptions {
        font_paths,
        ..Default::default()
    };

    office2pdf::convert_bytes(&input, format, &options)
        .map(|result| result.pdf)
        .map_err(|error| format!("Office conversion failed: {error}"))
}

fn read_font_directories(
    env: &mut JNIEnv<'_>,
    directories: &JObjectArray<'_>,
) -> Result<Vec<PathBuf>, String> {
    let count = env
        .get_array_length(directories)
        .map_err(|error| format!("Could not read Office font directory count: {error}"))?;
    let mut paths = Vec::with_capacity(count as usize);

    for index in 0..count {
        let value = env
            .get_object_array_element(directories, index)
            .map_err(|error| format!("Could not read Office font directory: {error}"))?;
        if value.is_null() {
            continue;
        }
        let value: String = env
            .get_string(&JString::from(value))
            .map_err(|error| format!("Could not decode Office font directory: {error}"))?
            .into();
        if !value.is_empty() {
            paths.push(PathBuf::from(value));
        }
    }

    Ok(paths)
}

fn throw_failure(env: &mut JNIEnv<'_>, message: String) -> jbyteArray {
    let _ = env.throw_new("java/lang/IllegalStateException", message);
    std::ptr::null_mut()
}
