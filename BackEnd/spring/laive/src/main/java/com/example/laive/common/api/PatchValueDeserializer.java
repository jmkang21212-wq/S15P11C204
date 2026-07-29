package com.example.laive.common.api;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * PatchValue의 "명시적 null"을 살려서 역직렬화한다.
 *
 * Jackson은 JSON null 토큰을 만나면 deserialize를 호출하지 않고 NullValueProvider만 부르므로,
 * getNullValue를 재정의하지 않으면 명시적 null과 필드 미전송이 똑같이 null이 되어 구분할 수 없다.
 */
public class PatchValueDeserializer extends ValueDeserializer<PatchValue<?>> {

    private final ValueDeserializer<Object> valueDeserializer;

    public PatchValueDeserializer() {
        this(null);
    }

    private PatchValueDeserializer(ValueDeserializer<Object> valueDeserializer) {
        this.valueDeserializer = valueDeserializer;
    }

    /** 선언된 제네릭 인자(PatchValue&lt;Instant&gt;의 Instant)로 실제 값 Deserializer를 찾는다. */
    @Override
    public ValueDeserializer<?> createContextual(
            DeserializationContext context,
            BeanProperty property
    ) {
        JavaType wrapperType = property != null ? property.getType() : context.getContextualType();
        JavaType valueType = wrapperType == null
                ? context.constructType(Object.class)
                : wrapperType.containedTypeOrUnknown(0);
        return new PatchValueDeserializer(
                context.findContextualValueDeserializer(valueType, property)
        );
    }

    @Override
    public PatchValue<?> deserialize(JsonParser parser, DeserializationContext context) {
        return PatchValue.of(valueDeserializer.deserialize(parser, context));
    }

    /** JSON에 null을 명시한 경우다. 래퍼를 살려 보내 "비우기"로 해석되게 한다. */
    @Override
    public Object getNullValue(DeserializationContext context) {
        return PatchValue.of(null);
    }

    /**
     * 필드를 아예 보내지 않은 경우다.
     *
     * record는 누락된 생성자 인자도 채워야 하는데 Jackson은 그 값을 getAbsentValue에서 얻는다.
     * 여기서 null을 돌려줘야 "변경 없음"이 "비우기"와 구분된다.
     */
    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return null;
    }
}
