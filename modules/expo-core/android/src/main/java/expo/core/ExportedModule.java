package expo.core;

import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import expo.core.interfaces.ExpoMethod;

/**
 * Abstract class for exported modules, i. e. modules which export some methods to client code.
 * Use {@link ExpoMethod} or override {@link ExportedModule#getExportedMethods()}
 * to export specific methods to client code and then {@link ExportedModule#invokeExportedMethod(String, Collection)}
 * to support them.
 */
public abstract class ExportedModule {
  private Context mContext;
  private Map<String, Method> mExportedMethods;

  public ExportedModule(Context context) {
    mContext = context;
  }

  public abstract String getName();

  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(Collections.<String, Object>emptyMap());
  }

  protected Context getContext() {
    return mContext;
  }

  /**
   * Creates a String-keyed map of methods exported from {@link ExportedModule},
   * i. e. methods annotated with {@link ExpoMethod}, which should be available in client code land.
   */
  public Map<String, Method> getExportedMethods() {
    if (mExportedMethods != null) {
      return mExportedMethods;
    }

    mExportedMethods = new HashMap<>();
    Method[] declaredMethodsArray = getClass().getDeclaredMethods();

    for (Method method : declaredMethodsArray) {
      if (method.getAnnotation(ExpoMethod.class) != null) {
        String methodName = method.getName();
        Class<?>[] methodParameterTypes = method.getParameterTypes();
        if (methodParameterTypes.length < 1) {
          throw new IllegalArgumentException(
                  "Method " + methodName + " of Java Module " + getName() + " does not define any arguments - minimum argument set is a Promise"
          );
        }

        Class<?> lastParameterClass = methodParameterTypes[methodParameterTypes.length - 1];

        if (lastParameterClass != expo.core.Promise.class) {
          throw new IllegalArgumentException(
                  "Last argument of method " + methodName + " of Java Module " + getName() + " does not expect a Promise"
          );
        }

        if (mExportedMethods.containsKey(methodName)) {
          throw new IllegalArgumentException(
                  "Java Module " + getName() + " method name already registered: " + methodName + "."
          );
        }

        mExportedMethods.put(methodName, method);
      }
    }

    return mExportedMethods;
  }

  /**
   * Invokes an exported method
   */
  public Object invokeExportedMethod(String methodName, Collection<Object> arguments) throws NoSuchMethodException, RuntimeException {
    Method method = mExportedMethods.get(methodName);

    if (method  == null) {
      throw new NoSuchMethodException("Module " + getName() + "does not export method " + methodName + ".");
    }

    int expectedArgumentsCount = method.getParameterTypes().length;
    if (arguments.size() != expectedArgumentsCount) {
      throw new IllegalArgumentException(
              "Method " + methodName + " on class " + getName() + " expects " + expectedArgumentsCount + " arguments, "
                      + "whereas " + arguments.size() + " arguments have been provided.");
    }

    Class<?>[] expectedArgumentClasses = method.getParameterTypes();
    Iterator<Object> actualArgumentsIterator = arguments.iterator();
    List<Object> transformedArguments = new ArrayList<>(arguments.size());

    for (int i = 0; i < expectedArgumentsCount; i++) {
      transformedArguments.add(transformArgumentToClass(actualArgumentsIterator.next(), expectedArgumentClasses[i]));
    }

    try {
      return method.invoke(this, transformedArguments.toArray());
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Exception occurred while executing exported method " + methodName
              + " on module " + getName() + ": " + e.getMessage(), e);
    }
  }

  protected Object transformArgumentToClass(Object argument, Class<?> expectedArgumentClass) {
    return ArgumentsHelper.transformArgumentToClass(argument, expectedArgumentClass);
  }
}